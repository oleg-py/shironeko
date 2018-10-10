package com.olegpy.shironeko

import scala.scalajs.js

import cats.effect.{CancelToken, ConcurrentEffect, IO}
import slinky.core.ComponentWrapper
import slinky.core.facade.ReactElement


trait SlinkyIntegration[F[_]] { this: StoreBase[F] =>
  abstract class Container[A](stream: => fs2.Stream[F, A]) {
    lazy val props = SlinkyIntegration.Impl.Props(
      stream,
      (render _).asInstanceOf[Any => ReactElement],
      F.asInstanceOf[ConcurrentEffect[Any]]
    )
    def apply() = SlinkyIntegration.Impl(props)
    def render(a: A): ReactElement
  }
}

object SlinkyIntegration {
  // Workaround for odd issue I'm yet to report to slinky
  private sealed trait Opt[+A]
  private case class Som[+A](a: A) extends Opt[A]
  private case object Non extends Opt[Nothing]
  // We cannot use inner classes, they cause runtime failure
  // b/c inner class' constuctor has outer object added as an implicit
  // first parameter per JVM spec. This is why this mess is necessary
  private[SlinkyIntegration] object Impl/*[F[_], A]*/ extends ComponentWrapper {
    type F[_] // We cheat by casting stream and effect to the same abstract type - this one
    case class Props(
      stream: fs2.Stream/*[F, A]*/[Any, Any],
      renderer: /*A*/Any => ReactElement,
      effect: ConcurrentEffect/*[F]*/[Any]
    )
    type State = Opt/*[A]*/[Any]
    class Def(jsProps: js.Object) extends Definition(jsProps) {
      def initialState: Opt/*[A]*/[Any] = Non
      private var cancelToken: CancelToken[IO] = _

      private def unsubscribe(): Unit =
        if (cancelToken != null) cancelToken.unsafeRunSync()

      override def componentWillMount(): Unit = {
        /*_*/
        unsubscribe()
        implicit val F: ConcurrentEffect[F] =
          props.effect.asInstanceOf[ConcurrentEffect[F]]
        val stream = props.stream.asInstanceOf[fs2.Stream[F, Any]]
        val execute = stream.evalMap(s => F.delay(setState(Som(s))))
          .compile.drain
        cancelToken = F.toIO(F.runCancelable(execute) {
          case Right(_) => IO.unit
          case Left(ex) => IO(ex.printStackTrace())
        }.unsafeRunSync())
        /*_*/
      }

      override def shouldComponentUpdate(nextProps: Props, nextState: Opt[Any]): Boolean = {
        nextState != state
      }

      override def componentWillUnmount(): Unit = {
        unsubscribe()
      }

      def render(): ReactElement = state match {
        case Som(a) => props.renderer(a)
        case Non => None
      }
    }
  }
}
