package com.olegpy.shironeko.todomvc.components

import slinky.core.Component
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.web.html._


@react class NewTodoInput extends Component {
  case class Props(onCommit: String => Unit)
  type State = String
  override def initialState: String = ""

  override def render(): ReactElement =
    input(
      className := "new-todo",
      placeholder := "What needs to be done?",
      autoFocus := true,
      value := state,
      onChange := { e => setState(e.target.value) },
      onKeyPress := { e =>
        if (e.key == "Enter") {
          props.onCommit(state)
          setState("")
        }
      }
    )
}
