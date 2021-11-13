package com.olegpy.shironeko.util

import scala.language.experimental.macros

import cats.effect.Concurrent


object combine {
  def apply[A] = new CombinePartiallyApplied[A]

  class CombinePartiallyApplied[A] {
    inline def from[F[_]](
      head: fs2.Stream[F, Any],
      head2: fs2.Stream[F, Any],
      rest: fs2.Stream[F, Any]*
    )(implicit F: Concurrent[F]): fs2.Stream[F, A] = scala.compiletime.error("NYI")
  }
}
