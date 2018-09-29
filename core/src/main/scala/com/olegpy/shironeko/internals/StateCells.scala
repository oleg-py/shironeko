package com.olegpy.shironeko.internals

import cats.data.State
import cats.effect.IO
import com.olegpy.shironeko.{Cell, StoreBase}
import fs2.concurrent.SignallingRef

trait StateCells[F[_]] { this: StoreBase[F] =>
  protected object Cell {
    def apply[A](initial: A): Cell[F, A] = new CellImpl(initial)
  }

  private class CellImpl[A](initial: A) extends Cell[F, A] {
    private[this] val underlying = SignallingRef[IO, A](initial).unsafeRunSync()

    def listen: fs2.Stream[F, A] = underlying.discrete.translate(natToF)

    def get: F[A] = underlying.get.to[F]
    def set(a: A): F[Unit] = underlying.set(a).to[F]
    def getAndSet(a: A): F[A] = underlying.getAndSet(a).to[F]

    def access: F[(A, A => F[Boolean])] = underlying.access.map {
      case (a, afb) => (a, (a: A) => afb(a).to[F])
    }.to[F]

    def tryUpdate(f: A => A): F[Boolean] = underlying.tryUpdate(f).to[F]

    def tryModify[B](f: A => (A, B)): F[Option[B]] = underlying.tryModify(f).to[F]

    def update(f: A => A): F[Unit] = underlying.update(f).to[F]

    def modify[B](f: A => (A, B)): F[B] = underlying.modify(f).to[F]

    def tryModifyState[B](state: State[A, B]): F[Option[B]] = underlying.tryModifyState(state).to[F]

    def modifyState[B](state: State[A, B]): F[B] =
      underlying.modifyState(state).to[F]
  }
}
