package com.olegpy.shironeko.todomvc.components

import com.olegpy.shironeko.util.shift
import org.scalajs.dom.Event
import slinky.core.StatelessComponent
import slinky.core.SyntheticEvent
import slinky.core.annotations.react
import slinky.core.facade.Hooks
import slinky.core.facade.ReactElement
import slinky.web.html._


@react class NewTodoInput extends StatelessComponent {
  case class Props(onCommit: String => Unit)

  override def render(): ReactElement = shift {
    val (text, setText) = Hooks.useState("")

    def handleChange(e: SyntheticEvent[input.tag.RefType, Event]): Unit =
      setText(e.target.value)

    input(
      className := "new-todo",
      placeholder := "What needs to be done?",
      autoFocus := true,
      value := text,
      onChange := { handleChange(_) },
      onKeyPress := { e =>
        if (e.key == "Enter") {
          props.onCommit(text)
          setText("")
        }
      }
    )
  }
}
