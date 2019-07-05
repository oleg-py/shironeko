package com.olegpy.shironeko.util

import slinky.core.FunctionalComponent
import slinky.core.facade.ReactElement


/**
  * `shift` creates a new FunctionalComponent render step from provided body
  *
  * React hooks cannot be used with regular class-based components, and separating
  * parts of state or using regular context API can be annoying. Shironeko Containers also
  * have no place to specify an extra local state, which might be desirable in several
  * scenarios.
  *
  * `shift` adds extra FunctionalComponent render layer, enabling use of hooks in both
  * class-based components and Shironeko `Container` components
  *
  * {{{
  * def render(stateFromStore: State): ReactElement = shift {
  *   implicit val locale = Hooks.useContext(LocaleCtx)
  *   val (name, setName) = Hooks.useState("")
  *   val (age, setAge) = Hooks.useState(22)
  *
  *   div(???)
  * }
  * }}}
  *
  */
object shift {
  private[this] val Shift = FunctionalComponent[() => ReactElement] { _() }
  def apply(f: => ReactElement): ReactElement = Shift(f _)
}
