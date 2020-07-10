package com.olegpy.shironeko

import cats.effect.{Concurrent, ConcurrentEffect}
import com.olegpy.shironeko.interop.Exec
import fs2.Pure
import slinky.core.KeyAddingStage
import slinky.core.facade.ReactElement


class DirectConnector[F[_], Algebra] { self =>
  def reportUncaughtException(e: Throwable): Unit = e.printStackTrace()
  private[this] object Underlying extends TaglessConnector[λ[f[_] => Algebra]] {
    override def reportUncaughtException(e: Throwable): Unit = self.reportUncaughtException(e)
  }

  private var ef: Underlying.Render[F] = _

  def apply(elem: ReactElement)(implicit F: ConcurrentEffect[F], store: Algebra): ReactElement = {
    ef = new Underlying.RenderInstance[F](store, F)
    Underlying(elem)(F, store)
  }

  def apply(store: Algebra)(elem: ReactElement)(implicit F: ConcurrentEffect[F]): ReactElement =
    apply(elem)(F, store)

  trait Container extends Exec.Boilerplate { self =>
    type State
    type Props

    protected def getClassName: String = self.getClass.getSimpleName

    private[this] object Impl extends Underlying.Container {
      override type State = self.State
      override type Props = self.Props

      override protected def getClassName: String = self.getClassName

      override def subscribe[f[_] : Subscribe]: fs2.Stream[f, State] = {
        self.subscribe.asInstanceOf[fs2.Stream[f, State]]
      }

      override def render[f[_] : Render](state: State, props: Props): ReactElement =
        self.render(state, props)

      // Forcing these guys into `public`
      override def getAlgebra[f[_] : Subscribe]: Algebra = super.getAlgebra
      override def getConcurrent[f[_] : Subscribe]: Concurrent[f] = super.getConcurrent
      override def getExec[f[_] : Render]: Exec[f] = super.getExec
    }

    protected implicit def getAlgebra: Algebra = ef.algebra
    protected implicit def getConcurrent: Concurrent[F] = ef.concurrent
    protected implicit def getExec: Exec[F] = ef.exec

    def apply(props: Props): KeyAddingStage = Impl(props)

    def subscribe: fs2.Stream[F, State]
    def render(state: State, props: Props): ReactElement
  }

  trait ContainerNoProps extends Container {
    def render(state: State): ReactElement

    def apply(): KeyAddingStage = apply(())
    final type Props = Unit
    final def render(state: State, props: Unit): ReactElement = render(state)
  }

  trait ContainerNoState extends Container {
    def render(props: Props): ReactElement

    type State = Unit
    final override def subscribe: fs2.Stream[Pure, Unit] = fs2.Stream(())
    final override def render(state: Unit, props: Props): ReactElement = render(props)
  }
}
