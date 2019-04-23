package com.olegpy.shironeko.util

import scala.reflect.macros.{TypecheckException, blackbox}


class Macros (val c: blackbox.Context) {

  import c.universe._


  def mkCallPosition: c.Expr[CallPosition] = {
    import c.universe._
    val pos = c.prefix.tree.pos
    val tpe = c.internal.enclosingOwner.fullName

    def lit[A: WeakTypeTag](a: A): c.Expr[A] = c.Expr(Literal(Constant(a)))

    reify {
      CallPosition(
        lit(tpe).splice,
        lit(pos.line).splice,
        lit(pos.column).splice)
    }
  }


  def combineMacro[F: c.WeakTypeTag, A: c.WeakTypeTag]
    (head: c.Tree, rest: c.Tree*)(F: c.Tree): c.Tree = {
    import c.universe._
    val args = (head :: rest.toList)
      .mapConserve(c.untypecheck)
      .mapConserve(ClearExtractors.transform)

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
    } catch {
      case TypecheckException(_, _) =>
        c.abort(c.enclosingPosition, s"Companion of ${weakTypeOf[A]} doesn't have an apply method")
    }

    val exprs = args.map { streamExpr =>
      q"_root_.cats.data.Nested(($streamExpr).holdOption($F))"
    }

    val functorFilter = q"_root_.fs2.Stream.functorFilterInstance[${weakTypeOf[F]}]"

    val nestedApplicative =
      q"""
      _root_.cats.data.Nested.catsDataApplicativeForNested(
        _root_.fs2.Stream.monadInstance[${weakTypeOf[F]}],
        _root_.fs2.concurrent.Signal.applicativeInstance($F)
      )
     """

    val tupleN = TermName("tuple" + arity)
    val mapN = TermName("map" + arity)
    val optApply = q"_root_.cats.instances.option.catsStdInstancesForOption"
    val tupleArg = TermName(c.freshName())

    val expanded = List.tabulate(arity) { i =>
      q"$tupleArg.${TermName("_" + (i + 1))}"
    }

    q"""
        $functorFilter.mapFilter(
          $nestedApplicative.$tupleN(..$exprs)
            .value.flatMap(_.discrete)
        ) { case $tupleArg => $optApply.$mapN(..$expanded)($A.apply) }
    """
  }


  private[this] val SELECTOR_DUMMY: TermName = TermName("<unapply-selector>")
  private[this] val UNAPPLY: TermName = TermName("unapply")
  private[this] val UNAPPLY_SEQ: TermName = TermName("unapplySeq")

  private object ClearExtractors extends Transformer {
    override def transform(tree: Tree): Tree = tree match {
      case UnApply(Apply(XSelect(qual, UNAPPLY | UNAPPLY_SEQ), List(Ident(SELECTOR_DUMMY))), pats) =>
        Apply(transform(qual), this.transformTrees(pats))
      case _ => super.transform(tree)
    }

    object XSelect {
      def unapply(tree: Tree): Option[(Tree, Name)] = tree match {
        case TypeApply(Select(fun, name), _) => Some((fun, name))
        case Select(fun, name) => Some((fun, name))
        case _ => None
      }
    }

  }
}