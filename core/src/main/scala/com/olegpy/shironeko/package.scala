package com.olegpy

import cats.effect.Sync
import fs2.{Pipe, Stream}
import fs2.concurrent.{SignallingRef, Topic}
import cats.implicits._

package object shironeko {
  type Cell[F[_], A] = SignallingRef[F, A]

  implicit class CellOps[F[_], A](private val self: Cell[F, A]) extends AnyVal{
    def listen: Stream[F, A] = self.discrete
  }

  type Events[F[_], A] = Topic[F, Option[A]]

  implicit class EventsOps[F[_], A](private val self: Events[F, A]) extends AnyVal {
    def emit: Pipe[F, A, Unit] = _.evalMap(emit1)
    def emit1(a: A): F[Unit] = self.publish1(a.some)
    def listen: Stream[F, A] = self.subscribe(1).tail.unNone
    def await1[B](pf: PartialFunction[A, B])(implicit F: Sync[F]): F[B] =
      listen.collectFirst(pf).compile.lastOrError
  }
}
