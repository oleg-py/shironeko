package com.olegpy.shironeko.todomvc.components

import com.olegpy.shironeko.todomvc.Filter
import slinky.core.StatelessComponent
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.web.html._


@react class Filters extends StatelessComponent {
  case class Props(
    activeCount: Int,
    filter: Filter,
    onClear: Option[() => Unit],
    setFilter: Filter => Unit
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
        Seq(Filter.All, Filter.Active, Filter.Completed).map { f =>
          val active = if (f == props.filter) "selected" else ""
          li()(a(className := active, onClick := { () => props.setFilter(f) })(f.toString))
        }: _*
      ),
      props.onClear.map { fn =>
        button(className := "clear-completed", onClick := fn)("Clear completed")
      }
    )
  }
}
