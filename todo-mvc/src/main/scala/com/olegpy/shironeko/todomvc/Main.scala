package com.olegpy.shironeko.todomvc

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportTopLevel, JSImport}
import scala.scalajs.LinkingInfo

import cats.effect.ExitCode
import cats.implicits._
import com.olegpy.shironeko.StoreDSL
import monix.eval.{Task, TaskApp}
import slinky.web.ReactDOM
import slinky.hot
import org.scalajs.dom

@JSImport("todomvc-app-css/index.css", JSImport.Default)
@js.native
object IndexCSS extends js.Object

object Main extends TaskApp {
  val css = IndexCSS

  @JSExportTopLevel("main")
  def main(): Unit = super.main(Array())

  override def run(args: List[String]): Task[ExitCode] = {
    initHotLoading >>
    StoreDSL[Task].use(new TodoStore(_).pure[Task])
      .product(getRoot)
      .flatMap { case (store, container) =>
        TodoLocalStorage.setup(store) >>
        Task(ReactDOM.render(Connector(store)(Routes()), container))
      } >> Task.never[ExitCode]
  }

  val getRoot = Task {
    Option(dom.document.getElementById("root")).getOrElse {
      val elem = dom.document.createElement("div")
      elem.id = "root"
      dom.document.body.appendChild(elem)
      elem
    }
  }

  val initHotLoading = Task {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }
  }
}
