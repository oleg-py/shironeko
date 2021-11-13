package com.olegpy.shironeko.util

import scala.language.experimental.macros

case class CallPosition(
  enclosingMember: String,
  line: Int,
  column: Int
)

object CallPosition {
  inline def provideCallPos: CallPosition = scala.compiletime.error("NYI")
}
