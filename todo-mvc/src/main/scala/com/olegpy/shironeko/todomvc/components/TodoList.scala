package com.olegpy.shironeko.todomvc.components

import com.olegpy.shironeko.todomvc.TodoItem
import org.scalajs.dom.Event
import slinky.core.Component
import slinky.core.SyntheticEvent
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.web.SyntheticFocusEvent
import slinky.web.SyntheticKeyboardEvent
import slinky.web.html._

@react class TodoList extends Component {
  case class Props(
    todos: Vector[TodoItem],
    onCheck: (Long, Boolean) => Unit,
    onDestroy: Long => Unit,
    onEdit: (Long, String) => Unit
  )

  case class State(editing: Option[(Long, String)])
  override def initialState: State = State(None)

  private[this] def classSet(cs: (Boolean, String)*) =
    cs.collect { case (true, a) => a }.mkString(" ")

  override def render(): ReactElement = {
    ul(className := "todo-list")(
      props.todos.zipWithIndex.map { case (item, idx) =>
        li(
          className := classSet(
            item.isCompleted -> "completed",
            state.editing.exists(_._1 == item.id) -> "editing"
          ),
          key := idx.toString
        )(
          div(className := "view")(
            input(
              className := "toggle",
              `type` := "checkbox",
              checked := item.isCompleted,
              onChange := handleCheckboxChange(item.id)
            ),
            label(item.text, onDoubleClick := { () =>
              setState(State(Some((item.id, item.text))))
            }),
            button(className := "destroy", onClick := { () =>
              props.onDestroy(item.id)
            })
          ),
          input(
            className := "edit",
            value := state.editing.filter(_._1 == item.id).fold(item.text)(_._2),
            onChange := handleEditChange(item.id),
            onBlur := { handleBlur(_) },
            onKeyPress := { handleKeyPress(_) }
          )
        )
      }
    )
  }

  private[this] def handleCheckboxChange(itemId: Long): SyntheticEvent[input.tag.RefType, Event] => Unit = {
    e => props.onCheck(itemId, e.target.checked)
  }

  private[this] def handleEditChange(itemId: Long): SyntheticEvent[input.tag.RefType, Event] => Unit = e => {
    val txt = e.target.value
    setState(State(Some((itemId, txt))))
  }

  private[this] def handleBlur(e: SyntheticFocusEvent[input.tag.RefType]): Unit = commit()

  private[this] def handleKeyPress(e: SyntheticKeyboardEvent[input.tag.RefType]): Unit = {
    if (e.key == "Enter") commit()
  }

  private[this] def commit(): Unit = {
    state.editing.foreach(props.onEdit.tupled)
    setState(initialState)
  }
}
