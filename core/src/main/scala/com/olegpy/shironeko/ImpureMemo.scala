package com.olegpy.shironeko

import cats.effect.concurrent.Deferred
import cats.effect.{Async, Effect, IO}

import java.util.concurrent.atomic.AtomicBoolean
import cats.effect.implicits._
import cats.implicits._

object ImpureMemo {
  def eager[F[_], A](fa: F[A])(implicit F: Effect[F]): F[A] = {
    val fa2 = lzy(fa)
    fa2.runAsync(IO.fromEither(_).void).unsafeRunSync()
    fa2
  }

  def lzy[F[_], A](fa: F[A])(implicit F: Async[F]): F[A] = {
    val d = Deferred.unsafeUncancelable[F, A]
    val isRunning = new AtomicBoolean(false)
    var done: A = null.asInstanceOf[A]

    F.suspend {
      if (isRunning.compareAndSet(false, true))
        fa.flatTap { a => F.delay { done = a } *> d.complete(a) }
      else if (done != null)
        F.pure(done)
      else
        d.get
    }
  }
}
