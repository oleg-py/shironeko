package com.olegpy.shironeko

import cats.effect.{Concurrent, Sync}
import fs2.{Pipe, Stream}
import fs2.concurrent.Topic

class Events[F[_], A] (val asTopic: Topic[F, Option[A]]) {
  def emit: Pipe[F, A, Unit] = _.evalMap(emit1)

  def emit1(a: A): F[Unit] = asTopic.publish1(Some(a))

  def listen: Stream[F, A] = asTopic.subscribe(1).tail.unNone

  def await1[B](pf: PartialFunction[A, B])(implicit F: Sync[F]): F[B] =
    listen.collectFirst(pf).compile.lastOrError

  def onNextDo(f: A => F[Unit])(implicit F: Concurrent[F]): F[Unit] =
    F.void(F.start(listen.evalMap(f).compile.drain))
}

object Events {
  def apply[F[_], A](topic: Topic[F, Option[A]]): Events[F, A] =
    new Events(topic)
}
