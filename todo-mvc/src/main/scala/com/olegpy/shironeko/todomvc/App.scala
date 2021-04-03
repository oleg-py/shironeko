package com.olegpy.shironeko.todomvc

import cats.effect.IO
import com.olegpy.shironeko.todomvc.components._
import cats.implicits._
import com.olegpy.shironeko.Stores
import com.olegpy.shironeko.interop.Exec
import com.olegpy.shironeko.slinky.streams.SlinkyContainer
import com.olegpy.shironeko.util.combine
import slinky.web.html._
import slinky.core.facade.{Fragment, ReactElement}


object TodoApp extends SlinkyContainer[IO] {
  type Props = Filter


  override def render(props: fs2.Stream[IO, Filter])(implicit F: Exec[IO], S: Stores[IO]): fs2.Stream[IO, ReactElement] =
    for {
      actions <- TodoActions.linkS[IO]

      (filter, todos) <- combine[(Filter, Vector[TodoItem])].from(
        props,
        TodoStore.linkS[IO].flatMap(_.todos.discrete)
      )

      visibleTodos = todos.filter(filter.matches)
      activeCount = todos.count(!_.isCompleted)
      hasCompleted = todos.exists(_.isCompleted)
      allCompleted = todos.forall(_.isCompleted)
    } yield {
      Fragment(
        section(className := "todoapp")(
          header(className := "header")(
            h1("todos"),
            NewTodoInput(toCallback(actions.createTodo _))
          ),
          if (todos.nonEmpty) {
            section(className := "main")(
              input(
                id := "toggle-all",
                className := "toggle-all",
                `type` := "checkbox",
                checked := allCompleted,
                onChange := { e =>
                  exec(actions.setAllStatus(e.target.checked))
                }
              ),
              label(
                htmlFor := "toggle-all",
                title := "Mark all as complete",
              ),
              TodoList(
                visibleTodos,
                toCallback(actions.setStatus),
                toCallback(actions.destroy),
                toCallback(actions.setText),
              ),
              Filters(
                activeCount,
                filter,
                toCallback(actions.clearCompleted).some.filter(_ => hasCompleted),
              )
            )
          } else None,
        ),
        TodoInfo()
      )
    }
}
