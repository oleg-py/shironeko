package com.olegpy.shironeko

import cats.data.State
import cats.effect.Concurrent
import fs2.Stream
import cats.effect.concurrent.Ref
import fs2.concurrent.SignallingRef
import cats.implicits._

trait Cell[F[_], A] extends Ref[F, A] {
  def listen: Stream[F, A]
}

object Cell {
  def apply[F[_]: Concurrent, A](a: A): Cell[F, A] = new Cell[F, A] {
    private[this] val underlying = ImpureMemo.lzy(SignallingRef[F, A](a))
    private[this] def delegate[B](f: SignallingRef[F, A] => F[B]): F[B] =
      underlying >>= f

    val listen: Stream[F, A] = Stream.force(underlying.map(_.discrete))

    def get: F[A] = delegate(_.get)
    def set(a: A): F[Unit] = delegate(_ set a)
    def getAndSet(a: A): F[A] = delegate(_ getAndSet a)
    def access: F[(A, A => F[Boolean])] = delegate(_.access)

    def tryUpdate(f: A => A): F[Boolean] = delegate(_ tryUpdate f)
    def tryModify[B](f: A => (A, B)): F[Option[B]] = delegate(_ tryModify f)
    def update(f: A => A): F[Unit] = delegate(_ update f)
    def modify[B](f: A => (A, B)): F[B] = delegate(_ modify f)

    def tryModifyState[B](state: State[A, B]): F[Option[B]] = delegate(_ tryModifyState state)
    def modifyState[B](state: State[A, B]): F[B] = delegate(_ modifyState state)
  }
}