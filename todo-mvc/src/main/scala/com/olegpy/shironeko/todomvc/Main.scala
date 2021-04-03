package com.olegpy.shironeko.todomvc

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportTopLevel, JSImport}
import scala.scalajs.LinkingInfo

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.olegpy.shironeko.slinky.SlinkyConnector
import slinky.web.ReactDOM
import slinky.hot
import org.scalajs.dom

@JSImport("todomvc-app-css/index.css", JSImport.Default)
@js.native
object IndexCSS extends js.Object

object Main extends IOApp.Simple {
  val css = IndexCSS

  @JSExportTopLevel("main")
  def main(): Unit = main(Array())


  override def run: IO[Unit] =
    SlinkyConnector.make[IO]
      .preAllocate(initHotLoading)
      .evalMap { conn =>
        for {
          todoStoreR <- conn.regStore(TodoStore.make)
          _          <- conn.regStore(todoStoreR.map(TodoActions.make))
          root       <- getRoot
          _          <- IO(ReactDOM.render(conn.Provider(Routes()), root))
        } yield ()
      }
      .useForever

  private val getRoot = IO {
    Option(dom.document.getElementById("root")).getOrElse {
      val elem = dom.document.createElement("div")
      elem.id = "root"
      dom.document.body.appendChild(elem)
      elem
    }
  }

  private val initHotLoading = IO {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }
  }
}
