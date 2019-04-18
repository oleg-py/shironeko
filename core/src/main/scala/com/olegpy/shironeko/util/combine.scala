package com.olegpy.shironeko.util

import scala.reflect.macros.{TypecheckException, blackbox}

import cats.effect.Concurrent


object combine {
  def apply[A] = new CombinePartiallyApplied[A]

  class CombinePartiallyApplied[A] {
    def from[F[_]](head: fs2.Stream[F, _], rest: fs2.Stream[F, _]*)(implicit F: Concurrent[F]): fs2.Stream[F, A] =
      macro combineMacro[F[_], A]
  }

  def combineMacro[F: c.WeakTypeTag, A: c.WeakTypeTag](c: blackbox.Context)
    (head: c.Tree, rest: c.Tree*)(F: c.Tree): c.Tree = {
    import c.universe._
    val args = head +: rest

    val arity = args.length
    if (arity > 22) {
      c.abort(c.enclosingPosition, "Macro combiner cannot handle > 22 args")
    }

    val A = symbolOf[A].companion
    if (A == NoSymbol) {
      c.abort(c.enclosingPosition, s"Couldn't find a companion of type ${weakTypeOf[A]}")
    }

    try {
      c.typecheck(q"$A.apply _")
    } catch { case TypecheckException(_, _) =>
      c.abort(c.enclosingPosition, s"Companion of ${weakTypeOf[A]} doesn't have an apply method")
    }

    val exprs = args.map { streamExpr =>
      q"_root_.cats.data.Nested(($streamExpr).holdOption($F))"
    }

    val functorFilter = q"_root_.fs2.Stream.functorFilterInstance[${weakTypeOf[F]}]"

    val nestedApplicative = q"""
      _root_.cats.data.Nested.catsDataApplicativeForNested(
        _root_.fs2.Stream.monadInstance[${weakTypeOf[F]}],
        _root_.fs2.concurrent.Signal.applicativeInstance($F)
      )
     """

    val tupleN = TermName("tuple" + arity)
    val mapN     =   TermName("map" + arity)
    val optApply = q"_root_.cats.instances.option.catsStdInstancesForOption"
    val tupleArg = TermName(c.freshName())

    val expanded = List.tabulate(arity) { i =>
      q"$tupleArg.${TermName("_" + (i + 1))}" }

    q"""
        $functorFilter.mapFilter(
          $nestedApplicative.$tupleN(..$exprs)
            .value.flatMap(_.discrete)
        ) { case $tupleArg => $optApply.$mapN(..$expanded)($A.apply) }
    """
  }
}
