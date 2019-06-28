package com.olegpy.shironeko.todomvc.components

import com.olegpy.shironeko.todomvc.TodoItem
import slinky.core.{Component, StatelessComponent}
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.web.html._
import cats.implicits._

@react class TodoList extends Component {
  case class Props(
    todos: Vector[TodoItem],
    onCheck: (Long, Boolean) => Unit,
    onDestroy: Long => Unit,
  )

  case class State(editing: Option[(Long, String)])
  override def initialState: State = State(None)

  override def render(): ReactElement = {
    ul(className := "todo-list")(
      props.todos.zipWithIndex.map { case (item, idx) =>
        li(
          className := { if (item.isCompleted) "completed" else "" },
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
            label(item.text),
            button(className := "destroy", onClick := { () =>
              props.onDestroy(item.id)
            })
          )
        )
      }
    )
  }
}
