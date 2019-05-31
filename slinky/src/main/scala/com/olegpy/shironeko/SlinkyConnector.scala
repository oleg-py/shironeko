package com.olegpy.shironeko

import cats.effect.{Concurrent, ConcurrentEffect, IO}
import slinky.core.{FunctionalComponent, KeyAddingStage}
import slinky.core.facade.{Hooks, React, ReactElement}
import cats.implicits._
import com.olegpy.shironeko.interop.Exec
import fs2.Pure

// TODO: improve implicits usage
class SlinkyConnector[Algebra[_[_]]] {
  private[shironeko] type Z[_]
  private[this] val ctx = React.createContext(null.asInstanceOf[Algebra[Z]])
  private[this] var F: ConcurrentEffect[Z] = _

  private[this] val providerFunc =
    FunctionalComponent[(Algebra[Z], ConcurrentEffect[Z], ReactElement)] {
      case (alg, f, elem) =>
        F = f
        ctx.Provider(alg)(elem)
    }

  def apply[F[_]](elem: ReactElement)(implicit F: ConcurrentEffect[F], store: Algebra[F]): ReactElement =
    apply(store)(elem)(F)

  def apply[F[_]: ConcurrentEffect](store: Algebra[F])(elem: ReactElement): ReactElement =
    providerFunc((store.asInstanceOf[Algebra[Z]], ConcurrentEffect[F].asInstanceOf[ConcurrentEffect[Z]], elem))



  trait ContainerF extends Exec.Boilerplate {
    type State[F[_]]
    type Props

    private[this] val impl = FunctionalComponent[Props] { props => {
      val alg = Hooks.useContext(ctx)
      val (storeState, setStoreState) = Hooks.useState(none[State[Z]])
      implicit val exec: Exec[Z] = new Exec[Z] {
        def unsafeRunLater[A](fa: Z[A]): Unit =
          F.runAsync(F.void(fa))(IO.fromEither).unsafeRunSync()
      }

      Hooks.useEffect(() => {
        implicit val ce: ConcurrentEffect[Z] = F
        val effect = subscribe(F, alg)
          .evalMap { e => F.delay(setStoreState(e.some))}
          .compile.drain

        val token = F.runCancelable(effect)(IO.fromEither).unsafeRunSync()
        () => exec.unsafeRunLater(token: Z[Unit])
      }, Seq())
      storeState.map(render[Z](_, props)(F, alg, exec))
    } }

    def apply(props: Props): KeyAddingStage = impl(props)

    def subscribe[F[_]: Concurrent: Algebra]: fs2.Stream[F, State[F]]
    def render[F[_]: Concurrent: Algebra: Exec](state: State[F], props: Props): ReactElement
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
    private[this] val underlying = new ContainerF {
      type State[F[_]] = self.State
      type Props = self.Props

      def subscribe[F[_]: Concurrent: Algebra] = self.subscribe[F]
      def render[F[_]: Concurrent: Algebra: Exec](state: State[F], props: Props) =
        self.render(state, props)
    }

    def apply(props: Props): KeyAddingStage = underlying.apply(props)

    def subscribe[F[_]: Concurrent: Algebra]: fs2.Stream[F, State]
    def render[F[_]: Concurrent: Algebra: Exec](state: State, props: Props): ReactElement
  }

  trait ContainerNoProps extends Container {
    def render[F[_]: Concurrent: Algebra: Exec](state: State): ReactElement

    final type Props = Unit
    def apply(): KeyAddingStage = apply(())

    final def render[F[_]: Concurrent: Algebra: Exec](state: State, props: Unit): ReactElement =
      render(state)
  }

  trait ContainerNoState extends Container {
    def render[F[_]: Concurrent: Algebra: Exec](props: Props): ReactElement

    type State = Unit
    final override def subscribe[F[_]: Concurrent: Algebra]: fs2.Stream[Pure, Unit] =
      fs2.Stream(())

    final override def render[F[_]: Concurrent: Algebra: Exec](state: Unit, props: Props): ReactElement =
      render(props)
  }
}
