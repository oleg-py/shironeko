package com.olegpy.shironeko.util

import scala.compiletime._
import scala.quoted._
import scala.deriving.Mirror

import cats.*
import cats.data.Nested
import cats.effect.Concurrent
import cats.implicits.given
import fs2.concurrent.Signal


object combine {
  def apply[Z] = new CombinePartiallyApplied[Z]

  class CombinePartiallyApplied[Z] {
    def from[F[_]: Concurrent, A, B](as: fs2.Stream[F, A], bs: fs2.Stream[F, B])(using Z: Coercer[(A, B), Z]): fs2.Stream[F, Z] =
      (as.holdOption, bs.holdOption).mapN { (ass, bss) => (ass.nested product bss.nested).value.discrete.unNone }.flatten.map(coerce)

    def from[F[_]: Concurrent, A, B, C](as: fs2.Stream[F, A], bs: fs2.Stream[F, B], cs: fs2.Stream[F, C])(using Z: Coercer[(A, B, C), Z]): fs2.Stream[F, Z] =
      (as.holdOption, bs.holdOption, cs.holdOption).mapN { (ass, bss, css) => (ass.nested, bss.nested, css.nested).tupled.value.discrete.unNone }.flatten.map(coerce)

    def from[F[_]: Concurrent, A, B, C, D](as: fs2.Stream[F, A], bs: fs2.Stream[F, B], cs: fs2.Stream[F, C], ds: fs2.Stream[F, D])(using Z: Coercer[(A, B, C, D), Z]): fs2.Stream[F, Z] =
      (as.holdOption, bs.holdOption, cs.holdOption, ds.holdOption).mapN { (ass, bss, css, dss) => (ass.nested, bss.nested, css.nested, dss.nested).tupled.value.discrete.unNone }.flatten.map(coerce)

    def from[F[_]: Concurrent, A, B, C, D, E](as: fs2.Stream[F, A], bs: fs2.Stream[F, B], cs: fs2.Stream[F, C], ds: fs2.Stream[F, D], es: fs2.Stream[F, E])(using Z: Coercer[(A, B, C, D, E), Z]): fs2.Stream[F, Z] =
      (as.holdOption, bs.holdOption, cs.holdOption, ds.holdOption, es.holdOption).mapN { (ass, bss, css, dss, ess) => (ass.nested, bss.nested, css.nested, dss.nested, ess.nested).tupled.value.discrete.unNone }.flatten.map(coerce)

    def from[F[_]: Concurrent, A, B, C, D, E, G](as: fs2.Stream[F, A], bs: fs2.Stream[F, B], cs: fs2.Stream[F, C], ds: fs2.Stream[F, D], es: fs2.Stream[F, E], gs: fs2.Stream[F, G])(using Z: Coercer[(A, B, C, D, E, G), Z]): fs2.Stream[F, Z] =
      (as.holdOption, bs.holdOption, cs.holdOption, ds.holdOption, es.holdOption, gs.holdOption).mapN { (ass, bss, css, dss, ess, gss) => (ass.nested, bss.nested, css.nested, dss.nested, ess.nested, gss.nested).tupled.value.discrete.unNone }.flatten.map(coerce)

    def from[F[_]: Concurrent, A, B, C, D, E, G, H](as: fs2.Stream[F, A], bs: fs2.Stream[F, B], cs: fs2.Stream[F, C], ds: fs2.Stream[F, D], es: fs2.Stream[F, E], gs: fs2.Stream[F, G], hs: fs2.Stream[F, H])(using Z: Coercer[(A, B, C, D, E, G, H), Z]): fs2.Stream[F, Z] =
      (as.holdOption, bs.holdOption, cs.holdOption, ds.holdOption, es.holdOption, gs.holdOption, hs.holdOption).mapN { (ass, bss, css, dss, ess, gss, hss) => (ass.nested, bss.nested, css.nested, dss.nested, ess.nested, gss.nested, hss.nested).tupled.value.discrete.unNone }.flatten.map(coerce)


    def from[F[_]: Concurrent, A, B, C, D, E, G, H, I](as: fs2.Stream[F, A], bs: fs2.Stream[F, B], cs: fs2.Stream[F, C], ds: fs2.Stream[F, D], es: fs2.Stream[F, E], gs: fs2.Stream[F, G], hs: fs2.Stream[F, H], is: fs2.Stream[F, I])(using Z: Coercer[(A, B, C, D, E, G, H, I), Z]): fs2.Stream[F, Z] =
      (as.holdOption, bs.holdOption, cs.holdOption, ds.holdOption, es.holdOption, gs.holdOption, hs.holdOption, is.holdOption).mapN { (ass, bss, css, dss, ess, gss, hss, iss) => (ass.nested, bss.nested, css.nested, dss.nested, ess.nested, gss.nested, hss.nested, iss.nested).tupled.value.discrete.unNone }.flatten.map(coerce)
  }


  type Coercer[A <: Tuple, Z] = Mirror.ProductOf[Z] { type MirroredElemTypes = A }
  def coerce[A <: Tuple, Z](using Z: Coercer[A, Z]): A => Z = Z.fromProduct
}

inline def meth[T <: Tuple](args: T) = println(args)

@main def runTest() = {
  combine[(Int, String)].from(
  fs2.Stream(42).covary[cats.effect.IO],
  fs2.Stream("hoh").covary[cats.effect.IO]
  )
}
