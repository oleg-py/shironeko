package com.olegpy.shironeko.todomvc

import cats.effect.IO
import com.olegpy.shironeko.Store


class TodoActions (S: TodoStore) {
  def setText(id: Long, text: String): IO[Unit] =
    S.todos.update(_.collect {
      case todo if todo.id == id => todo.copy(text = text)
      case other => other
    })

  def createTodo(text: String): IO[Unit] =
    S.freshId
     .map(TodoItem(_, text))
     .flatMap(todo => S.todos.update(_ :+ todo))

  def setAllStatus(complete: Boolean): IO[Unit] =
    S.todos.update(_.map(_.copy(isCompleted = complete)))

  def setStatus(id: Long, complete: Boolean): IO[Unit] =
    S.todos.update(_.map {
      case TodoItem(`id`, text, _) => TodoItem(id, text, complete)
      case other => other
    })

  def destroy(id: Long): IO[Unit] =
    S.todos.update(_.filterNot(_.id == id))

  def setFilter(f: Filter): IO[Unit] =
    S.filter.set(f)

  def clearCompleted: IO[Unit] =
    S.todos.update(_.filterNot(_.isCompleted))
}

object TodoActions extends Store.Companion[TodoActions] {
  def make(base: TodoStore) = new TodoActions(base)
}
