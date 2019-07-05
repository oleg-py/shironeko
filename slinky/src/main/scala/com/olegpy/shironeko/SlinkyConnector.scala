package com.olegpy.shironeko

import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, IO, Timer}
import slinky.core.{FunctionalComponent, KeyAddingStage}
import slinky.core.facade.{Hooks, React, ReactElement}
import cats.implicits._
import com.olegpy.shironeko.interop.Exec
import fs2.Pure

// TODO: improve implicits usage
class SlinkyConnector[Algebra[_[_]]] { conn =>
  // We use an "unknown" erased type to represent whatever the algebra is used with
  private[shironeko] type Z[_]

  // Dummy types used as cast safety evidences for several implicits
  sealed trait Subscribe[f[_]] // = (Concurrent[f], Algebra[f])
  sealed trait Render[f[_]] extends Subscribe[f] // = (Concurrent[f], Algebra[f], Exec[f])

  private[this] val ctxAlg = React.createContext(null.asInstanceOf[Algebra[Z]])
  private[this] val ctxF = React.createContext(null.asInstanceOf[ConcurrentEffect[Z]])

  private[this] val providerFunc =
    FunctionalComponent[(Algebra[Z], ConcurrentEffect[Z], ReactElement)] {
      case (alg, f, elem) =>
        ctxF.Provider(f)(ctxAlg.Provider(alg)(elem))
    }

  def apply[F[_]](elem: ReactElement)(implicit F: ConcurrentEffect[F], store: Algebra[F]): ReactElement =
    apply(store)(elem)(F)

  def apply[F[_]: ConcurrentEffect](store: Algebra[F])(elem: ReactElement): ReactElement =
    providerFunc((store.asInstanceOf[Algebra[Z]], ConcurrentEffect[F].asInstanceOf[ConcurrentEffect[Z]], elem))


  trait ContainerF extends Exec.Boilerplate {
    type State[F[_]]
    type Props

    // Copying this over, for ergonomics
    final type Subscribe[f[_]] = conn.Subscribe[f]
    final type Render[f[_]] = conn.Render[f]

    private[this] var CEInstance: ConcurrentEffect[Z] = _
    private[this] var AlgInstance: Algebra[Z] = _
    private[this] var ExecInstance: Exec[Z] = _

    protected implicit def getAlgebra[F[_]: Subscribe]: Algebra[F] =
      AlgInstance.asInstanceOf[Algebra[F]]

    protected implicit def getConcurrent[F[_]: Subscribe]: Concurrent[F] =
      CEInstance.asInstanceOf[Concurrent[F]]

    protected implicit def getExec[F[_]: Render]: Exec[F] =
      ExecInstance.asInstanceOf[Exec[F]]

    private[this] val impl = FunctionalComponent[Props] { props => {
      val F = Hooks.useContext(ctxF)
      val alg = Hooks.useContext(ctxAlg)
      Hooks.useEffect(() => {
        CEInstance = F
        AlgInstance = alg
        ExecInstance = Exec.fromEffect(F)
      }, Seq(F, alg))

      val (storeState, setStoreState) = Hooks.useState(none[State[Z]])
      implicit val dummy: Render[Z] = null

      Hooks.useEffect(() => {
        val effect = subscribe[Z]
          .evalMap { e => F.delay(setStoreState(e.some))}
          .compile.drain

        val token = F.runCancelable(effect)(IO.fromEither).unsafeRunSync()
        () => ExecInstance.unsafeRunLater(token: Z[Unit])
      }, Seq())
      storeState.map(state => render[Z](state, props))
    } }

    def apply(props: Props): KeyAddingStage = impl(props)

    def subscribe[F[_]: Subscribe]: fs2.Stream[F, State[F]]
    def render[F[_]: Render](state: State[F], props: Props): ReactElement
  }

  trait ContainerFNoProps extends ContainerF {
    def render[F[_]: Concurrent: Algebra: Exec](state: State[F]): ReactElement


    type Props = Unit
    def apply(): KeyAddingStage = apply(())
    final def render[F[_]: Concurrent: Algebra: Exec](state: State[F], props: Unit): ReactElement =
      render(state)
  }

  trait Container extends Exec.Boilerplate { self =>
    type State
    type Props

    // Copying these over, for ergonomics
    final type Subscribe[f[_]] = conn.Subscribe[f]
    final type Render[f[_]] = conn.Render[f]

    private[this] object underlying extends ContainerF {
      type State[F[_]] = self.State
      type Props = self.Props

      def subscribe[F[_]: Subscribe] = self.subscribe[F]
      def render[F[_]: Render](state: State[F], props: Props) =
        self.render(state, props)

      // Forcing these guys into `public`
      override def getAlgebra[F[_] : Subscribe]: Algebra[F] = super.getAlgebra
      override def getConcurrent[F[_] : Subscribe]: Concurrent[F] = super.getConcurrent
      override def getExec[F[_] : Render]: Exec[F] = super.getExec
    }

    protected implicit def getAlgebra[F[_]: Subscribe]: Algebra[F] =
      underlying.getAlgebra[F]

    protected implicit def getConcurrent[F[_]: Subscribe]: Concurrent[F] =
      underlying.getConcurrent[F]

    protected implicit def getExec[F[_]: Render]: Exec[F] =
      underlying.getExec[F]

    def apply(props: Props): KeyAddingStage = underlying.apply(props)

    def subscribe[F[_]: Subscribe]: fs2.Stream[F, State]
    def render[F[_]: Render](state: State, props: Props): ReactElement
  }

  trait ContainerNoProps extends Container {
    def render[F[_]: Render](state: State): ReactElement

    final type Props = Unit
    def apply(): KeyAddingStage = apply(())

    final def render[F[_]: Render](state: State, props: Unit): ReactElement =
      render(state)
  }

  trait ContainerNoState extends Container {
    def render[F[_]: Render](props: Props): ReactElement

    type State = Unit
    final override def subscribe[F[_]: Subscribe]: fs2.Stream[Pure, Unit] =
      fs2.Stream(())

    final override def render[F[_]: Render](state: Unit, props: Props): ReactElement =
      render(props)
  }
}
