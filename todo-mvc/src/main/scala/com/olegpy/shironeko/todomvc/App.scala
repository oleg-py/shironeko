package com.olegpy.shironeko.todomvc

import com.olegpy.shironeko.todomvc.components._
import slinky.web.html._
import com.olegpy.shironeko.util.combine
import monix.eval.Task
import slinky.core.facade.{Fragment, ReactElement}


object App extends Connector.ContainerNoProps {
  case class State(
    todos: Vector[TodoItem],
    filter: Filter
  ) {
    val visibleTodos: Vector[TodoItem] = todos.filter(filter.matches)
    val activeCount: Int = todos.count(!_.isCompleted)
    val allCompleted: Boolean = todos.forall(_.isCompleted)
  }

  override def subscribe: fs2.Stream[Task, State] =
    combine[State].from(
      TodoStore().todos.discrete,
      TodoStore().filter.discrete
    )

  override def render(state: State): ReactElement = Fragment(
    section(className := "todoapp")(
      header(className := "header")(
        h1("todos"),
        NewTodoInput(toCallback(TodoActions.createTodo _))
      ),
      if (state.todos.nonEmpty) {
        section(className := "main")(
          input(
            id := "toggle-all",
            className := "toggle-all",
            `type` := "checkbox",
            checked := state.allCompleted,
            onChange := { e =>
              exec(TodoActions.setAllStatus(e.target.checked))
            }
          ),
          label(
            htmlFor := "toggle-all",
            title := "Mark all as complete",
          ),
          TodoList(
            state.visibleTodos,
            toCallback(TodoActions.setStatus _),
            toCallback(TodoActions.destroy _)
          ),
        )
      } else None,
    ),
    TodoInfo()
  )
}
