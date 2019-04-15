package com.olegpy.shironeko.interop

abstract class Exec[F[_]] private[shironeko]() {
  def unsafeRunLater[A](fa: F[A]): Unit
}
