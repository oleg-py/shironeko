package com.olegpy.shironeko.todomvc

import monix.eval.Task


object TodoActions {
  def createTodo(text: String)(implicit S: TodoStore): Task[Unit] =
    S.freshId
     .map(TodoItem(_, text))
     .flatMap(todo => S.todos.update(_ :+ todo))

  def setAllStatus(complete: Boolean)(implicit S: TodoStore): Task[Unit] =
    S.todos.update(_.map(_.copy(isCompleted = complete)))

  def setStatus(id: Long, complete: Boolean)(implicit S: TodoStore): Task[Unit] =
    S.todos.update(_.map {
      case TodoItem(`id`, text, _) => TodoItem(id, text, complete)
      case other => other
    })

  def destroy(id: Long)(implicit S: TodoStore): Task[Unit] =
    S.todos.update(_.filterNot(_.id == id))
}
