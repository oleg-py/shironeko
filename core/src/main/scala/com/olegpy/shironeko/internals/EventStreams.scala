package com.olegpy.shironeko.internals

import cats.effect.IO
import com.olegpy.shironeko.{Events, StoreBase}
import fs2.Pipe
import fs2.concurrent.Topic
import cats.syntax.flatMap._

trait EventStreams[F[_]] { this: StoreBase[F] =>
  protected object Events {
    def apply[A]: Events[F, A] = new EventsImpl[A]
    def handled[A](f: A => F[Unit]): Events[F, A] = {
      val events = apply[A]
      F.toIO(events.listen.evalMap(f).compile.drain).unsafeRunAsyncAndForget()
      events
    }
  }

  private class EventsImpl[A] extends Events[F, A] {
    private val underlying = Topic[IO, Option[A]](None).unsafeRunSync()

    def emit: Pipe[F, A, Unit] = _.evalMap(emit1)

    def emit1(a: A): F[Unit] = F.delay {
      underlying.publish1(Some(a)).unsafeRunAsyncAndForget()
    }

    def listen: fs2.Stream[F, A] =
      underlying
        .subscribe(1)
        .translate(natToF)
        .tail
        .unNone

    def await1[B](pf: PartialFunction[A, B]): F[B] =
      listen.collectFirst(pf).compile.last.flatMap {
        case None => F.never
        case Some(a) => F.pure(a)
      }
  }
}
