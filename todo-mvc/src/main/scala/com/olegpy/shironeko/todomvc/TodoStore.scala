package com.olegpy.shironeko.todomvc

import cats.effect.Fiber
import cats.implicits._
import com.olegpy.shironeko.StoreDSL
import com.olegpy.shironeko.util.combine
import monix.eval.Task
import org.scalajs.dom.window

//noinspection TypeAnnotation
class TodoStore (dsl: StoreDSL[Task]) {
  import dsl._

  val todos  = cell(Vector(
    TodoItem(-3L, "Create TodoMVC for Shironeko", isCompleted = true),
    TodoItem(-2L, "??????"),
    TodoItem(-1L, "Profit!"),
  ))
  val filter = cell[Filter](Filter.All)

  private[this] val idRef = ref(0L)
  def freshId: Task[Long] = idRef.modify(x => (x + 1, x))
}

object TodoStore {
  def apply()(implicit ev: TodoStore): TodoStore = ev
}

object TodoLocalStorage {
  private[this] val ls = window.localStorage
  private[this] val key = "todo-state"
  private[this] val separator = "\u0000"
  private[this] def save(filter: Filter, todos: Vector[TodoItem]): Task[Unit] = {
    val fields: Vector[TodoItem => String] = Vector(_.id.toString, _.text, _.isCompleted.toString)
    val content = filter.toString ++ separator ++ todos.flatMap(fields.mapApply).mkString(separator)
    Task(ls.setItem(key, content))
  }

  private[this] def load: Task[Option[(Filter, Vector[TodoItem])]] = Task {
    Option(ls.getItem(key)).flatMap { s =>
      val parts = s.split(separator).toList
      val filter = parts.headOption.flatMap {
        case "All" => Filter.All.some
        case "Active" => Filter.Active.some
        case "Completed" => Filter.Completed.some
        case _ => none
      }
      val items = parts.drop(1).grouped(3).flatMap {
        case a :: b :: c :: Nil => Some(TodoItem(a.toLong, b, c.toBoolean))
        case _ => None
      }.toVector

      filter.tupleRight(items)
    }
  }

  def setup(s: TodoStore): Task[Fiber[Task, Unit]] = {
    load.flatMap(_.traverse_ { case (filter, todos) =>
      s.filter.set(filter) >> s.todos.set(todos)
    }).onErrorHandle(_ => Task {
      println("LocalStorage Todos are corrupted. Clearing...")
      ls.removeItem(key)
    }) >>
    combine[(Filter, Vector[TodoItem])]
      .from(
        s.filter.discrete,
        s.todos.discrete
      )
      .evalMap(Function.tupled(save))
      .compile.drain.start
  }
}

case class TodoItem(
  id: Long,
  text: String,
  isCompleted: Boolean = false
)

sealed abstract class Filter(val matches: TodoItem => Boolean)
object Filter {
  case object All extends Filter(_ => true)
  case object Active extends Filter(!_.isCompleted)
  case object Completed extends Filter(_.isCompleted)
}