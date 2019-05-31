package com.olegpy.shironeko.interop

abstract class Exec[F[_]] private[shironeko]() {
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
  }
}