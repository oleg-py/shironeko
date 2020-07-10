package com.olegpy.shironeko.todomvc

import com.olegpy.shironeko.util.shift
import slinky.core.ExternalComponent
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import typings.reactRouter.mod.RouteProps
import typings.reactRouterDom.components.HashRouter
import typings.reactRouterDom.components.Route
import typings.reactRouterDom.components.{Link => JSLink}

import scala.scalajs.js

object Routes {

  def apply(): ReactElement = shift {
    HashRouter(
      Route(RouteProps(
        exact = true,
        path = "/",
        render = _ => TodoApp(Filter.All)
      )),
      Route(RouteProps(
        path = "/active",
        render = _ => TodoApp(Filter.Active)
      )),
      Route(RouteProps(
        path = "/completed",
        render = _ => TodoApp(Filter.Completed)
      ))
    )
  }

  @react object Link extends ExternalComponent {
    case class Props(
      to: String,
      className: js.UndefOr[String]
    )
    val component = JSLink.component
  }
}
