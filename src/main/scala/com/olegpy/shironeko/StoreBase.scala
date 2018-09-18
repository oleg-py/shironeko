package com.olegpy.shironeko

import scala.concurrent.ExecutionContext

import cats.arrow.FunctionK
import cats.effect.{ConcurrentEffect, ContextShift, IO}
import cats.effect.implicits._
import cats.implicits._
import com.olegpy.shironeko.internals.{EventStreams, Preloading, StateCells}

class StoreBase[F[_]](instance: => ConcurrentEffect[F])
  extends StateCells[F] with EventStreams[F] with Preloading[F]
{
  implicit protected def F: ConcurrentEffect[F] = instance

  type Action = F[Unit]

  def exec(a: Action): Unit = a.toIO.unsafeRunAsyncAndForget()
  def execS(f: this.type => Action): Unit = exec(f(this))

  implicit private[shironeko] val ioShift: ContextShift[IO] = new ContextShift[IO] {
    def shift: IO[Unit] = F.unit.start.void.toIO

    def evalOn[A](ec: ExecutionContext)(fa: IO[A]): IO[A] =
      IO.raiseError(new UnsupportedOperationException(
        "Internal ContextShift failure. This is a bug in Shironeko"))
  }

  private[shironeko] val natToF = new FunctionK[IO, F] {
    def apply[A](fa: IO[A]): F[A] = F.liftIO(fa)
  }

  private[shironeko] val natToIO = new FunctionK[F, IO] {
    def apply[A](fa: F[A]): IO[A] = F.toIO(fa)
  }
}
