package com.olegpy.shironeko.todomvc.components

import slinky.core.StatelessComponent
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.web.html._


@react class TodoInfo extends StatelessComponent {
  override type Props = Unit

  override def render(): ReactElement = {
    footer(className := "info")(
      p("Double-click to edit a todo"),
      p("Created by ", a(href := "https://olegpy.com")("Oleg Pyzhcov")),
      p("Part of ", a(href := "http://todomvc.com")("TodoMVC"))
    )
  }
}
