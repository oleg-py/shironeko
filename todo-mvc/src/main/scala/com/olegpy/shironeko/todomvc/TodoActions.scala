package com.olegpy.shironeko.todomvc

import monix.eval.Task


class TodoActions (private val S: TodoStore) extends AnyVal {
  def createTodo(text: String): Task[Unit] =
    S.freshId
     .map(TodoItem(_, text))
     .flatMap(todo => S.todos.update(_ :+ todo))

  def setAllStatus(complete: Boolean): Task[Unit] =
    S.todos.update(_.map(_.copy(isCompleted = complete)))

  def setStatus(id: Long, complete: Boolean): Task[Unit] =
    S.todos.update(_.map {
      case TodoItem(`id`, text, _) => TodoItem(id, text, complete)
      case other => other
    })

  def destroy(id: Long): Task[Unit] =
    S.todos.update(_.filterNot(_.id == id))

  def setFilter(f: Filter): Task[Unit] =
    S.filter.set(f)

  def clearCompleted: Task[Unit] =
    S.todos.update(_.filterNot(_.isCompleted))
}

object TodoActions {
  def apply()(implicit S: TodoStore) = new TodoActions(S)
}
