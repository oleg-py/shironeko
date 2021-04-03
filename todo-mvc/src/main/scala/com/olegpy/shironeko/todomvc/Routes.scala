package com.olegpy.shironeko.todomvc

import scala.annotation.nowarn
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core.{ExternalComponent, ExternalComponentNoProps, ExternalComponentWithAttributes, FunctionalComponent}
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.web.html.a

@react object Routes {
  type Props = Unit
  val component = FunctionalComponent[Unit] { _ =>
    import ReactRouter._
    HashRouter(
      Route(path = Seq("/", "/:segment"), render = { p =>
        Option[Any](p.`match`.params.segment).collect {
          case "active" => Filter.Active
          case "completed" => Filter.Completed
          case () => Filter.All
        }.map { filter => TodoApp(filter) }
      }, exact = true),
    )
  }
}

object ReactRouter {
  @JSImport("react-router-dom", JSImport.Default)
  @js.native
  @nowarn
  object Raw extends js.Object {
    val HashRouter: js.Object    = js.native
    val Route: js.Object         = js.native
    val Link: js.Object          = js.native
  }

  object HashRouter extends ExternalComponentNoProps {
    val component = Raw.HashRouter
  }

  @react object Route extends ExternalComponent {
    case class Props(
      path: Seq[String],
      render: js.Dynamic => ReactElement,
      exact: Boolean = false
    )

    val component = Raw.Route
  }

  @react object Link extends ExternalComponentWithAttributes[a.tag.type] {
    case class Props(to: String)
    val component = Raw.Link
  }
}