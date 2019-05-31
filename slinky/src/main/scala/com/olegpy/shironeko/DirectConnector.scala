package com.olegpy.shironeko

import cats.effect.{Concurrent, ConcurrentEffect, IO, Sync}
import cats.implicits._
import com.olegpy.shironeko.interop.Exec
import slinky.core.{FunctionalComponent, KeyAddingStage}
import slinky.core.facade.{Hooks, React, ReactElement}


class DirectConnector[F[_], Algebra] {
  private[this] val ctx = React.createContext(null.asInstanceOf[Algebra])
  private[this] var F: ConcurrentEffect[F] = _

  private[this] val providerFunc =
    FunctionalComponent[(Algebra, ConcurrentEffect[F], ReactElement)] {
      case (alg, f, elem) =>
        F = f
        ctx.Provider(alg)(elem)
    }

  def apply(elem: ReactElement)(implicit F: ConcurrentEffect[F], store: Algebra): ReactElement =
    apply(store)(elem)(F)

  def apply(store: Algebra)(elem: ReactElement)(implicit F: ConcurrentEffect[F]): ReactElement =
    providerFunc((store, F, elem))

  trait Container extends Exec.Boilerplate {
    type State
    type Props

    private[this] val impl = FunctionalComponent[Props] { props => {
      val alg = Hooks.useContext(ctx)
      val (storeState, setStoreState) = Hooks.useState(none[State])
      implicit val exec: Exec[F] = new Exec[F] {
        def unsafeRunLater[A](fa: F[A]): Unit =
          F.runAsync(F.void(fa))(IO.fromEither).unsafeRunSync()
      }

      implicit val CC: Sync[F] = F

      //noinspection DuplicatedCode
      Hooks.useEffect(() => {
        val effect = subscribe(F, alg)
          .evalMap { e =>
            F.delay(setStoreState(e.some))}
          .compile.drain

        val token = F.runCancelable(effect)(IO.fromEither).unsafeRunSync()
        () => exec.unsafeRunLater(token)
      }, Seq())
      storeState.map(render(_, props)(F, alg, exec))
    } }

    def apply(props: Props): KeyAddingStage = impl(props)

    def subscribe(implicit F: Concurrent[F], Algebra: Algebra): fs2.Stream[F, State]
    def render(state: State, props: Props)(implicit F: Concurrent[F], Algebra: Algebra, E: Exec[F]): ReactElement
  }

  trait ContainerNoProps extends Container {
    def render(state: State)(implicit F: Concurrent[F], Algebra: Algebra, E: Exec[F]): ReactElement

    type Props = Unit

    def apply(): KeyAddingStage = apply(())

    final def render(state: State, props: Props)(implicit F: Concurrent[F], Algebra: Algebra, E: Exec[F]): ReactElement =
      render(state)
  }

  trait ContainerNoState extends Container {
    def render(props: Props)(implicit F: Concurrent[F], Algebra: Algebra, E: Exec[F]): ReactElement

    type State = Unit
    final override def subscribe(implicit F: Concurrent[F], Algebra: Algebra): fs2.Stream[F, Unit] =
      fs2.Stream(())

    final override def render(state: Unit, props: Props)(implicit F: Concurrent[F], Algebra: Algebra, E: Exec[F]): ReactElement =
      render(props)
  }
}
