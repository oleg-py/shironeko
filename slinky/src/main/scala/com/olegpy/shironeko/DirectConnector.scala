package com.olegpy.shironeko

import cats.effect.{Concurrent, ConcurrentEffect}
import com.olegpy.shironeko.interop.Exec
import fs2.Pure
import slinky.core.{KeyAddingStage}
import slinky.core.facade.{ReactElement}


class DirectConnector[F[_], Algebra] { self =>
  def reportUncaughtException(e: Throwable): Unit = e.printStackTrace()
  private[this] object Underlying extends TaglessConnector[λ[f[_] => Algebra]] {
    override def reportUncaughtException(e: Throwable): Unit = self.reportUncaughtException(e)
  }

  def apply(elem: ReactElement)(implicit F: ConcurrentEffect[F], store: Algebra): ReactElement =
    Underlying(elem)(F, store)

  def apply(store: Algebra)(elem: ReactElement)(implicit F: ConcurrentEffect[F]): ReactElement =
    Underlying(store)(elem)(F)

  trait Container extends Exec.Boilerplate { self =>
    type State
    type Props

    private[this] object Impl extends Underlying.Container {
      override type State = self.State
      override type Props = self.Props

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

    protected implicit def getAlgebra: Algebra = Impl.getAlgebra[F](null)
    protected implicit def getConcurrent: Concurrent[F] = Impl.getConcurrent[F](null)
    protected implicit def getExec: Exec[F] = Impl.getExec[F](null)

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
