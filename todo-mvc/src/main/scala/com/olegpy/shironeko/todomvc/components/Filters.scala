package com.olegpy.shironeko.todomvc.components

import com.olegpy.shironeko.todomvc.Filter
import com.olegpy.shironeko.todomvc.Routes.Link
import slinky.core.StatelessComponent
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.web.html._


@react class Filters extends StatelessComponent {
  case class Props(
    activeCount: Int,
    filter: Filter,
    onClear: Option[() => Unit],
  )

  override def render(): ReactElement = {
    footer(className := "footer")(
      span(className := "todo-count")(
        strong(props.activeCount),
        " ",
        if (props.activeCount == 1) "item" else "items",
        " left"
      ),
      ul(className := "filters")(
        Seq(Filter.All -> "/", Filter.Active -> "/active", Filter.Completed -> "/completed").map { case (f, to) =>
          val active = if (f == props.filter) "selected" else ""
          li()(Link(to, active)(f.toString))
        }: _*
      ),
      props.onClear.map { fn =>
        button(className := "clear-completed", onClick := fn)("Clear completed")
      }
    )
  }
}
