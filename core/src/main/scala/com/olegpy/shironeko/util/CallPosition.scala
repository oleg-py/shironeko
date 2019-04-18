package com.olegpy.shironeko.util

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

case class CallPosition(
  enclosingMember: String,
  line: Int,
  column: Int
)

object CallPosition {
  implicit def provideCallPos: CallPosition = macro synthesize

  def synthesize(c: blackbox.Context): c.Expr[CallPosition] = {
    import c.universe._
    val pos = c.prefix.tree.pos
    val tpe = c.internal.enclosingOwner.fullName
    def lit[A: WeakTypeTag](a: A): c.Expr[A] = c.Expr(Literal(Constant(a)))

    reify {
      CallPosition(
        lit(tpe).splice,
        lit(pos.line).splice,
        lit(pos.column).splice)}
  }
}
