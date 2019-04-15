package com.olegpy.shironeko.util

import cats.data.Nested
import cats.effect.Concurrent
import cats.sequence._
import fs2.Stream
import fs2.concurrent.Signal
import shapeless.{Generic, HList, Poly1}
import cats.implicits._

object combine {
  type N[F[_], A] = Nested[Stream[F, ?], Signal[F, ?], A]
  def apply[A]: CombinePartiallyApplied[A] =
    new CombinePartiallyApplied[A]

  object holdFn extends Poly1 {
    implicit def holdStream[F[_]: Concurrent, A]: Case.Aux[Stream[F, A], N[F, Option[A]]] =
      at(s => Nested(s.holdOption))
  }

  class CombinePartiallyApplied[A] {
    def fromStreamsProduct[F[_], Streams <: HList, Opts <: HList, Repr <: HList](streams: Streams)(implicit
      tr: Traverser.Aux[Streams, holdFn.type, N[F, Opts]], // fused 2-3
      seq2: Sequencer.Aux[Opts, Option, Repr],                                            // 5
      gen: Generic.Aux[A, Repr]                                                           // 6
    ): Stream[F, A] =
      streams.traverse(holdFn).value
        .flatMap(_.discrete)
        .mapFilter(_.sequence)
        .map(gen.from)
  }
}
