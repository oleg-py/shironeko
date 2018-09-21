package com.olegpy.shironeko.internals

import cats.data.State
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import com.olegpy.shironeko.{Cell, StoreBase}
import fs2.concurrent.Topic

trait StateCells[F[_]] { this: StoreBase[F] =>
  protected object Cell {
    def apply[A](initial: A): Cell[F, A] = new CellImpl(initial)
  }

  private class CellImpl[A](initial: A) extends Cell[F, A] {
    private[this] val underlying = Ref.unsafe[F, A](initial)
    private[this] val topic = Topic[IO, A](initial).unsafeRunSync()
    private[this] def notify(a: A) = topic.publish1(a).to[F]

    def listen: fs2.Stream[F, A] = topic.subscribe(1).translate(natToF)

    def get: F[A] = underlying.get
    def set(a: A): F[Unit] = notify(a) >> underlying.set(a)
    def getAndSet(a: A): F[A] = notify(a) >> underlying.getAndSet(a)

    def access: F[(A, A => F[Boolean])] = underlying.access.map { case (get, set) =>
      val newSet = (a: A) => set(a).flatTap {
        case true  => notify(a)
        case false => F.unit
      }
      (get, newSet)
    }

    def tryUpdate(f: A => A): F[Boolean] =
      tryModify(a => (f(a), ())).map(_.nonEmpty)

    def tryModify[B](f: A => (A, B)): F[Option[B]] =
      modify(f).map(Some(_)) // TODO: I'm too lazy to implement that properly

    def update(f: A => A): F[Unit] = modify(a => (f(a), ()))

    def modify[B](f: A => (A, B)): F[B] =
      underlying
        .modify[(A, B)] { oldA =>
          val (newA, b) =f(oldA)
          (newA, (newA, b))
        }
        .flatTap { case (newA, _) => notify(newA) }
        .map { case (_, b) => b}

    def tryModifyState[B](state: State[A, B]): F[Option[B]] =
      tryModify(state.run(_).value)

    def modifyState[B](state: State[A, B]): F[B] =
      modify(state.run(_).value)
  }
}
