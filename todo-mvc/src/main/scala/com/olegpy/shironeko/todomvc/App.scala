package com.olegpy.shironeko.todomvc

import com.olegpy.shironeko.todomvc.components._
import cats.implicits._
import slinky.web.html._
import monix.eval.Task
import slinky.core.facade.{Fragment, ReactElement}


object TodoApp extends Connector.Container {
  type Props = Filter
  type State = Vector[TodoItem]

  override def subscribe: fs2.Stream[Task, State] =
    TodoStore().todos.discrete

  override def render(todos: State, filter: Props): ReactElement = {
    val visibleTodos: Vector[TodoItem] = todos.filter(filter.matches)
    val activeCount: Int = todos.count(!_.isCompleted)
    val hasCompleted: Boolean = todos.exists(_.isCompleted)
    val allCompleted: Boolean = todos.forall(_.isCompleted)
    Fragment(
      section(className := "todoapp")(
        header(className := "header")(
          h1("todos"),
          NewTodoInput(toCallback(TodoActions().createTodo _))
        ),
        if (todos.nonEmpty) {
          section(className := "main")(
            input(
              id := "toggle-all",
              className := "toggle-all",
              `type` := "checkbox",
              checked := allCompleted,
              onChange := { e =>
                exec(TodoActions().setAllStatus(e.target.checked))
              }
            ),
            label(
              htmlFor := "toggle-all",
              title := "Mark all as complete",
            ),
            TodoList(
              visibleTodos,
              toCallback(TodoActions().setStatus _),
              toCallback(TodoActions().destroy _)
            ),
            Filters(
              activeCount,
              filter,
              toCallback(TodoActions().clearCompleted).some.filter(_ => hasCompleted),
            )
          )
        } else None,
      ),
      TodoInfo()
    )
  }
}
