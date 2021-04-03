package com.olegpy.shironeko.kernel

import scala.collection.immutable.ArraySeq
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import cats.{ApplicativeThrow, MonadThrow}
import cats.data.{EitherNel, EitherT, Ior, IorT, NonEmptyList}
import cats.implicits._
import cats.effect.implicits._
import cats.effect.{Deferred, Outcome, Ref, Resource}
import cats.effect.kernel.{Concurrent, DeferredSink, DeferredSource, Fiber, GenConcurrent, Spawn, Temporal, Unique}
import com.olegpy.shironeko.kernel.ResourcePool.Key
import fs2.CompositeFailure


trait ResourcePool[F[_]] {
  def lookup[A](key: Key[A]): F[Option[Resource[F, A]]]
  def register[A](key: Key[A], resource: Resource[F, A], policy: ResourcePool.Policy): F[Unit]
}

object ResourcePool {
  case class Key[A](private val token: Unique.Token)
  object Key {
    def make[F[_], A](implicit F: Unique[F]): F[Key[A]] =
      F.applicative.map(F.unique)(apply[A])
  }
  case class Policy(finalization: Finalization, reacquisition: Reacquisition)

  sealed trait Finalization
  object Finalization {
    case object Immediately extends Finalization
    case class  After(dur: FiniteDuration) extends Finalization
    case object Never extends Finalization
  }

  sealed trait Reacquisition
  object Reacquisition {
    case object Concurrently  extends Reacquisition
    case object Synchronously extends Reacquisition
  }

  def apply[F[_]: Temporal]: Resource[F, ResourcePool[F]] =
    Resource.make(Ref[F].of(new State[F])) { ref =>
      IorT(ref.getAndUpdate(_.die).flatMap(_.finalizeAll))
        .iterateWhile(done => done)
        .value.map(_.left)
        .flatMap(_.traverse_ {
          case NonEmptyList(hd, hd2 :: rest) =>
            new CompositeFailure(hd, NonEmptyList(hd2, rest)).raiseError[F, Unit]
          case NonEmptyList(hd, Nil) =>
            hd.raiseError[F, Unit]
        })
    }.map { stateRef =>
      def fix[A](f: (=> F[A]) => F[A]): F[A] = {
        lazy val fa: F[A] = f(fa)
        fa
      }

      def mkPooled[A](key: Key[A]) = {
        def runAndUnsetOrDie(fin: F[Unit]) =
          (fin >> stateRef.update(_.unsetState(key))).onError {
            case NonFatal(ex) => stateRef.update(_.setState(key, PooledState.Dead(ex)))
          }


        def selfCancel[B] = Concurrent[F].canceled >> Concurrent[F].never[B]

        val release: F[Unit] =
          fix[Unit] { retry =>
            for {
              fiberB <- FiberLater[F, Throwable, Unit]
              finished <- Deferred[F, Unit]
              action <- stateRef.modify { state =>
                state.getState(key) match {
                  // Illegal states: None, any with refcount < 1, any with awaiting finalization
                  case _ if state.dead => state -> state.playDead[Unit]
                  case Some(s @ PooledState.Opening(f, 1)) =>
                    state.getPolicy(key).finalization match {
                      case Finalization.Never =>
                        // We started it, we keep going no matter what
                        state.setState(key, s.copy(refCount = 0)) -> ().pure[F]
                      case _ =>
                        state.setState(key, PooledState.CriticalSection(finished)) -> (f.cancel >> f.join.flatMap {
                          case Outcome.Succeeded(fa) =>
                            fa.flatMap { case (a, fin) =>
                              stateRef.update(_.setState(key, PooledState.Open(a, fin, 1))) >>
                              retry
                            }
                          case Outcome.Errored(e) =>
                            stateRef.update(_.setState(key, PooledState.Dead(e)))
                          case Outcome.Canceled() =>
                            stateRef.update(_.unsetState(key))
                        } >> completeOnce(finished))
                    }
                  case Some(s @ PooledState.Opening(_, n)) if n > 1 =>
                    state.setState(key, s.copy(refCount = n - 1)) -> ().pure[F]
                  case Some(s @ PooledState.Open(value, finalizer, 1)) =>
                    state.getPolicy(key).finalization match {
                      case Finalization.Immediately =>
                        state.setState(key, PooledState.Finalizing(finished)) ->
                        (runAndUnsetOrDie(finalizer) >> completeOnce(finished))
                      case Finalization.After(dur) =>
                        state.setState(key, PooledState.AwaitingFinalization(value, finalizer, fiberB)) ->
                        Concurrent[F].uncancelable { poll =>
                          poll(Temporal[F].sleep(dur)).onCancel {
                            stateRef.update(_.setState(key, PooledState.Open(value, finalizer, 0)))
                          } >>
                          stateRef.update(_.setState(key, PooledState.Finalizing(finished))) >>
                          runAndUnsetOrDie(finalizer) >>
                          completeOnce(finished)
                        }.start.flatMap(fiberB.complete)
                      case Finalization.Never =>
                        state.setState(key, s.copy(refCount = 0)) -> ().pure[F]
                    }
                  case Some(s @ PooledState.Open(_, _, n)) if n > 1 =>
                    state.setState(key, s.copy(refCount = n - 1)) -> ().pure[F]
                  case Some(PooledState.Dead(ex)) =>
                    state -> ex.raiseError[F, Unit]
                  case illegal =>
                    throw new IllegalStateException(s"Unexpected ResourcePool state: $illegal")
                }
              }
              _ <- action
            } yield ()
          }


        Resource.makeFull[F, A] { poll =>
          fix[A] { retry =>
            for {
              fiberA <- FiberLater[F, Throwable, (A, F[Unit])]
              lock <- Deferred[F, Unit]
              action <- stateRef.modify { state =>
                def freshOpen = state.setState(key, PooledState.Opening(fiberA, 1)) -> {
                  state.getCtor(key).allocated.start
                    .flatTap(fiberA.complete)
                    .flatMap(f => poll(f.joinWith(selfCancel)).onCancel(release))
                    .map(_._1)
                }
                state.getState(key) match {
                  case _ if state.dead => state -> state.playDead[A]
                  case Some(ps @ PooledState.Opening(src, rc)) =>
                    state.setState(key, ps.copy(refCount = rc + 1)) ->
                    poll(src.joinWith(selfCancel).map(_._1)).onCancel(release)
                  case Some(ps @ PooledState.Open(src, _, rc)) =>
                    state.setState(key, ps.copy(refCount = rc + 1)) ->
                    src.pure[F]

                  case Some(PooledState.AwaitingFinalization(value, finalizer, fiber)) =>
                    state.setState(key, PooledState.CriticalSection(lock)) -> (fiber.cancel >> fiber.join.flatMap {
                      case Outcome.Canceled() =>
                        stateRef.update(_.setState(key, PooledState.Open(value, finalizer, 1))) >>
                        completeOnce(lock).as(value)
                      case Outcome.Succeeded(_) =>
                        // Other fiber is expected to reset state there
                        completeOnce(lock) >> retry // already began, retry
                      case Outcome.Errored(e) =>
                        stateRef.update(_.setState(key, PooledState.Dead(e))) >>
                        finalizer.guarantee(completeOnce(lock)) >>
                        e.raiseError[F, A]
                    })

                  case Some(PooledState.Finalizing(finished)) =>
                    state.getPolicy(key).reacquisition match {
                      case Reacquisition.Concurrently =>
                        freshOpen
                      case Reacquisition.Synchronously =>
                        state -> (poll(finished.get) >> retry)
                    }
                  case Some(PooledState.CriticalSection(ended)) =>
                    state -> (poll(ended.get) >> retry)
                  case Some(PooledState.Dead(ex)) =>
                    state -> ex.raiseError[F, A]
                  case None => freshOpen
                }
              }
              a <- action
            } yield a
          }
        }(_ => release)
      }

      new ResourcePool[F] {
        override def lookup[A](key: Key[A]): F[Option[Resource[F, A]]] = {
          for {
            state <- stateRef.get
            _ <- state.guard
          } yield Option.when(state.has(key))(mkPooled(key))
        }

        override def register[A](key: Key[A], resource: Resource[F, A], policy: Policy): F[Unit] =
          stateRef.modify { state =>
            if (state.dead) {
              state -> state.guard
            } else if (state.has(key)) {
              state -> new IllegalStateException(s"Duplicate pool key: $key").raiseError[F, Unit]
            } else {
              state.setCtor(key, resource).setPolicy(key, policy) -> ().pure[F]
            }
          }.flatten
      }
    }

  private def completeOnce[F[_]: MonadThrow](d: DeferredSink[F, Unit]) =
    d.complete(()).ifM(
      ().pure[F],
      new IllegalStateException("Attempt to complete Deferred twice").raiseError[F, Unit]
    )

  private class State[F[_]](
    states: Map[Key[_], PooledState[F, _]] = Map.empty[Key[_], PooledState[F, _]],
    ctors: Map[Key[_], Resource[F, _]] = Map.empty[Key[_], Resource[F, _]],
    policies: Map[Key[_], Policy] = Map.empty[Key[_], Policy],
    val dead: Boolean = false,
  ) {
    def getState[A](key: Key[A]): Option[PooledState[F, A]] =
      states.get(key).asInstanceOf[Option[PooledState[F, A]]]
    def setState[A](key: Key[A], state: PooledState[F, A]): State[F] =
      new State(states.updated(key, state), ctors, policies)

    def unsetState[A](key: Key[A]): State[F] =
      new State(states - key, ctors, policies)

    def has(key: Key[_]): Boolean = ctors.contains(key)

    def getCtor[A](key: Key[A]): Resource[F, A] =
      ctors(key).asInstanceOf[Resource[F, A]] // Unsafe, only to be used in valid states
    def setCtor[A](key: Key[A], ctor: Resource[F, A]) =
      new State(states, ctors.updated(key, ctor), policies)

    def getPolicy[A](key: Key[A]): Policy = policies(key) // Unsafe, only to be used in valid states

    def setPolicy[A](key: Key[A], policy: Policy) =
      new State(states, ctors, policies.updated(key, policy))

    def die: State[F] = new State[F](dead = true)

    def playDead[A](implicit F: ApplicativeThrow[F]): F[A] =
      F.raiseError(new IllegalStateException("The pool is already closed"))
    def guard(implicit F: ApplicativeThrow[F]): F[Unit] =
      if (dead) playDead[Unit]
      else F.unit

    def finalizeAll(implicit F: Spawn[F]): F[Ior[NonEmptyList[Throwable], Boolean]] = {
      import cats.effect.instances.spawn.parallelForGenSpawn // TODO - is this a good idea?
      def attemptNel(fu: F[Unit]): F[EitherNel[Throwable, Unit]] = fu.attempt.map(_.toEitherNel)
      val allGood = states.values.forall {
        case PooledState.CriticalSection(_) => false
        case _ => true
      }
      states.values.to(ArraySeq).parTraverse_ {
        case PooledState.Opening(src, _) =>
          EitherT(src.cancel >> src.join.flatMap[EitherNel[Throwable, Unit]] {
            case Outcome.Canceled() => ().rightNel[Throwable].pure[F]
            case Outcome.Errored(e) => e.leftNel[Unit].pure[F]
            case Outcome.Succeeded(fa) => attemptNel(fa.flatMap(_._2))
          })
        case PooledState.Open(_, fin, _) => EitherT(attemptNel(fin))
        case PooledState.AwaitingFinalization(_, fin, fib) => EitherT(attemptNel(fin >> fib.cancel))
        case PooledState.Finalizing(finished) => EitherT(attemptNel(finished.get))
        case PooledState.Dead(ex) => EitherT.fromEither[F](ex.leftNel[Unit])
        case PooledState.CriticalSection(wait) => EitherT(attemptNel(wait.get))
      }.value.map {
        case Left(errs) => Ior.both(errs, allGood)
        case Right(_)   => Ior.right(allGood)
      }
    }
  }

  private sealed trait PooledState[+F[_], +A]
  private object PooledState {
    case class Opening[F[_], A](src: Fiber[F, Throwable, (A, F[Unit])], refCount: Int) extends PooledState[F, A]
    case class Open[F[_], A](value: A, finalizer: F[Unit], refCount: Int) extends PooledState[F, A]
    case class AwaitingFinalization[F[_], A](value: A, finalizer: F[Unit], fiber: Fiber[F, Throwable, Unit]) extends PooledState[F, A]
    case class Finalizing[F[_]](finished: DeferredSource[F, Unit]) extends PooledState[F, Nothing]
    case class CriticalSection[F[_]](ended: DeferredSource[F, Unit]) extends PooledState[F, Nothing]
    case class Dead(error: Throwable) extends PooledState[Nothing, Nothing]
  }

  private trait FiberLater[F[_], E, A] extends Fiber[F, E, A] with DeferredSink[F, Fiber[F, E, A]]
  private object FiberLater {
    def apply[F[_], E, A](implicit F: GenConcurrent[F, _]) =
      F.deferred[Fiber[F, E, A]].map { d =>
        new FiberLater[F, E, A] {
          override def complete(a: Fiber[F, E, A]): F[Boolean] = d.complete(a)
          override def cancel: F[Unit] = d.get.flatMap(_.cancel).uncancelable
          override def join: F[Outcome[F, E, A]] = d.get.flatMap(_.join)
        }
      }
  }
}