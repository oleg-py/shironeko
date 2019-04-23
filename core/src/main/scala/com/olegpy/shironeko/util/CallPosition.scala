package com.olegpy.shironeko.util

import scala.language.experimental.macros

case class CallPosition(
  enclosingMember: String,
  line: Int,
  column: Int
)

object CallPosition {
  implicit def provideCallPos: CallPosition =
    macro Macros.mkCallPosition
}
