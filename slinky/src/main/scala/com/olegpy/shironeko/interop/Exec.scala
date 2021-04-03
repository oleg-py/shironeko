package com.olegpy.shironeko.interop

import cats.effect.std.Dispatcher


trait Exec[F[_]] {
  def unsafeRunLater[A](fa: F[A]): Unit
}

object Exec {
  trait Boilerplate {
    final protected def exec[F[_]: Exec](action: F[Unit]): Unit =
      implicitly[Exec[F]].unsafeRunLater(action)

    final protected def toCallback[F[_]: Exec](action: F[Unit]): () => Unit =
      () => exec(action)

    final protected def toCallback[F[_]: Exec, A](action: A => F[Unit]): A => Unit =
      a => exec(action(a))

    final protected def toCallback[F[_]: Exec, A, B](action: (A, B) => F[Unit]): (A, B) => Unit =
      (a, b) => exec(action(a, b))

    final protected def toCallback[F[_]: Exec, A, B, C](action: (A, B, C) => F[Unit]): (A, B, C) => Unit =
      (a, b, c) => exec(action(a, b, c))

    final protected def toCallback[F[_]: Exec, A, B, C, D](action: (A, B, C, D) => F[Unit]): (A, B, C, D) => Unit =
      (a, b, c, d) => exec(action(a, b, c, d))

    final protected def toCallback[F[_]: Exec, A, B, C, D, E](action: (A, B, C, D, E) => F[Unit]): (A, B, C, D, E) => Unit =
      (a, b, c, d, e) => exec(action(a, b, c, d, e))
  }

  def fromDispatcher[F[_]](disp: Dispatcher[F]): Exec[F] = new Exec[F] {
    override def unsafeRunLater[A](fa: F[A]): Unit = disp.unsafeRunAndForget(fa)
  }
}