package com.olegpy.shironeko.todomvc

import scala.scalajs.js

import com.olegpy.shironeko.util.shift
import slinky.core.ExternalComponent
import slinky.core.annotations.react
import typings.reactLib.ScalableSlinky._
import typings.reactDashRouterDashDomLib.reactDashRouterDashDomLibComponents.{Link => JSLink, _}
import typings.reactDashRouterLib.reactDashRouterMod.RouteProps

object Routes {
  def apply() = shift {
    HashRouter.noprops(
      Route[RouteProps].props(RouteProps(
        exact = true,
        path = "/",
        render = _ => TodoApp(Filter.All)
      )),
      Route[RouteProps].props(RouteProps(
        path = "/active",
        render = _ => TodoApp(Filter.Active)
      )),
      Route[RouteProps].props(RouteProps(
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
    val component = JSLink
  }
}
