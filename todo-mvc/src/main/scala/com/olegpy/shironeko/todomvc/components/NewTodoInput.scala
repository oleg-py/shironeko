package com.olegpy.shironeko.todomvc.components

import com.olegpy.shironeko.util.shift
import slinky.core.StatelessComponent
import slinky.core.annotations.react
import slinky.core.facade.{Hooks, ReactElement}
import slinky.web.html._


@react class NewTodoInput extends StatelessComponent {
  case class Props(onCommit: String => Unit)

  override def render(): ReactElement = shift {
    val (text, setText) = Hooks.useState("")
    input(
      className := "new-todo",
      placeholder := "What needs to be done?",
      autoFocus := true,
      value := text,
      onChange := { e => setText(e.target.value) },
      onKeyPress := { e =>
        if (e.key == "Enter") {
          props.onCommit(text)
          setText("")
        }
      }
    )
  }
}
