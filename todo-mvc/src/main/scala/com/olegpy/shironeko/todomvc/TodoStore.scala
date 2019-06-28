package com.olegpy.shironeko.todomvc

import com.olegpy.shironeko.StoreDSL
import monix.eval.Task


//noinspection TypeAnnotation
class TodoStore (dsl: StoreDSL[Task]) {
  import dsl._

  val todos  = cell(Vector.empty[TodoItem])
  val filter = cell[Filter](Filter.All)

  private[this] val idRef = ref(0L)
  def freshId: Task[Long] = idRef.modify(x => (x + 1, x))
}

object TodoStore {
  def apply()(implicit ev: TodoStore): TodoStore = ev
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