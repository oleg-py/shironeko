package com.olegpy.shironeko.internals

import scala.concurrent.{ExecutionContext, Promise}

import cats.effect.IO
import com.olegpy.shironeko.StoreBase


trait Preloading[F[_]] { this: StoreBase[F] =>
  protected def preload[A](fa: F[A]): F[A] = {
    val p = Promise[A]
    F.runAsync(fa) {
      case Left(ex) => IO(p.failure(ex))
      case Right(value) => IO(p.success(value))
    }.unsafeRunSync()
    F.async(cb => p.future.onComplete { try_ =>
      cb(try_.toEither)
    }(new ExecutionContext {
      def execute(runnable: Runnable): Unit = runnable.run()
      def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
    }))
  }
}
