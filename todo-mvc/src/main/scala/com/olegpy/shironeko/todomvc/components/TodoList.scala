package com.olegpy.shironeko.todomvc.components

import com.olegpy.shironeko.todomvc.TodoItem
import slinky.core.{Component, StatelessComponent, SyntheticEvent}
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.web.html._
import cats.implicits._

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
              onChange := { e =>
                props.onCheck(item.id, e.target.checked)
              }
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
            onChange := { e =>
              val txt = e.target.value
              setState(State(Some((item.id, txt))))
            },
            onBlur := { commit(_) },
            onKeyPress := { e =>
              if (e.key == "Enter") commit(e)
            }
          )
        )
      }
    )
  }
  private[this] def commit(e: SyntheticEvent[input.tag.RefType, Any]) = {
    state.editing.foreach(props.onEdit.tupled)
    setState(initialState)
  }
}
