package com.olegpy.shironeko

import cats.effect.{Concurrent, IO}


class StoreSyntax[F[_]] {
  object events {
    def apply[A](implicit F: Concurrent[F]): Events[F, A] = Events[F, A]
    def handled[A](f: A => F[Unit])(implicit F: Concurrent[F]): Events[F, A] = Events.handled(f)
  }

  object cell {
    def apply[A](a: A)(implicit F: Concurrent[F]): Cell[F, A] = Cell[F, A](a)
  }
}

object StoreSyntax {
  val io = new StoreSyntax[IO]
}
