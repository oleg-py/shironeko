package com.olegpy.shironeko.util

import scala.language.experimental.macros

import cats.effect.Concurrent


object combine {
  def apply[A] = new CombinePartiallyApplied[A]

  class CombinePartiallyApplied[A] {
    def from[F[_]](
      head: fs2.Stream[F, _],
      head2: fs2.Stream[F, _],
      rest: fs2.Stream[F, _]*
    )(implicit F: Concurrent[F]): fs2.Stream[F, A] =
      macro Macros.combineMacro[F[_], A]
  }
}
