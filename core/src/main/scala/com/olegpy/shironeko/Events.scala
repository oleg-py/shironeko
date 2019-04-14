package com.olegpy.shironeko

import cats.effect.Concurrent
import fs2.concurrent.Topic
import fs2.{Pipe, Stream}
import cats.implicits._
import cats.effect.implicits._

trait Events[F[_], A] {
  def emit: Pipe[F, A, Unit]
  def emit1(a: A): F[Unit]
  def listen: Stream[F, A]
  def await1[B](pf: PartialFunction[A, B]): F[B]
}

object Events {
  def apply[F[_]: Concurrent, A]: Events[F, A] =
    handled(Function.const(().pure[F]))

  def handled[F[_]: Concurrent, A](f: A => F[Unit]): Events[F, A] = {
    new Events[F, A] {
      private[this] val underlying = ImpureMemo.lzy {
        for {
          topic <- Topic[F, Option[A]](None)
          _     <- topic.subscribe(1).unNone.evalMap(f).compile.drain.start
        } yield topic
      }

      val emit: Pipe[F, A, Unit] = _.evalMap(emit1)
      def emit1(a: A): F[Unit] =
        underlying.flatMap(_.publish1(a.some)).start.void
      val listen: Stream[F, A] =
        Stream.force(underlying.map(_.subscribe(1).tail)).unNone
      def await1[B](pf: PartialFunction[A, B]): F[B] =
        listen.collectFirst(pf).compile.lastOrError
    }
  }
}