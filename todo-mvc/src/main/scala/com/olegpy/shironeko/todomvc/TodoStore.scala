package com.olegpy.shironeko.todomvc

import cats.effect.kernel.{Ref, Resource}
import cats.effect.IO
import cats.implicits._
import com.olegpy.shironeko.Store
import com.olegpy.shironeko.util.combine
import fs2.concurrent.SignallingRef
import org.scalajs.dom.window

//noinspection TypeAnnotation
class TodoStore (
  val todos: SignallingRef[IO, Vector[TodoItem]],
  val filter: SignallingRef[IO, Filter],
  idRef: Ref[IO, Long]
) {
  val freshId: IO[Long] = idRef.getAndUpdate(_ + 1L)
}

object TodoStore extends Store.Companion[TodoStore] {
  def make: Resource[IO, TodoStore] =
    for {
      r <- Resource.eval(LocalStorage.load.handleError(_ => None))
      (filter, todos) = r.getOrElse(defaults)
      store <- Resource.eval {
        (
          SignallingRef[IO, Vector[TodoItem]](todos),
          SignallingRef[IO, Filter](filter),
          Ref[IO].of(todos.map(_.id).maxOption.orEmpty)
        ).mapN(new TodoStore(_, _, _))
      }
      _ <- LocalStorage.persistAll(store).background
    } yield store


  private def defaults = (
    Filter.All,
    Vector(
      TodoItem(-3L, "Create TodoMVC for Shironeko", isCompleted = true),
      TodoItem(-2L, "??????"),
      TodoItem(-1L, "Profit!"),
    ),
  )

  private object LocalStorage {
    private[this] val ls = window.localStorage
    private[this] val key = "todo-state"
    private[this] val separator = "\u0000"

     def load: IO[Option[(Filter, Vector[TodoItem])]] = IO {
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

    def persistAll(s: TodoStore): IO[Unit] = {
      combine[(Filter, Vector[TodoItem])]
        .from(
          s.filter.discrete,
          s.todos.discrete
        )
        .evalMap(Function.tupled(save))
        .compile.drain
    }

    private[this] def save(filter: Filter, todos: Vector[TodoItem]) = {
      val fields: Vector[TodoItem => String] = Vector(_.id.toString, _.text, _.isCompleted.toString)
      val content = filter.toString ++ separator ++ todos.flatMap(fields.mapApply).mkString(separator)
      IO(ls.setItem(key, content))
    }
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