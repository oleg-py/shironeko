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

  // Enable use of hooks in custom `def render`
  private[this] val shift = FunctionalComponent[ReactElement] { el => el }

  def apply(elem: ReactElement)(implicit F: ConcurrentEffect[F], store: Algebra): ReactElement =
    apply(store)(elem)(F)

  def apply(store: Algebra)(elem: ReactElement)(implicit F: ConcurrentEffect[F]): ReactElement =
    providerFunc((store, F, elem))

  trait Container extends Exec.Boilerplate {
    type State
    type Props

    private[this] var algebraInstanceMutable: Algebra = _

    implicit protected def execInstance: Exec[F] = Exec.fromEffect(F)
    implicit protected def concurrentInstance: Concurrent[F] = F
    implicit protected def algebraInstance: Algebra = algebraInstanceMutable


    private[this] val impl = FunctionalComponent[Props] { props => {
      val alg = Hooks.useContext(ctx)
      val (storeState, setStoreState) = Hooks.useState(none[State])

      //noinspection DuplicatedCode
      Hooks.useEffect(() => {
        algebraInstanceMutable = alg
        val effect = subscribe
          .evalMap { e =>
            F.delay(setStoreState(e.some))}
          .compile.drain

        val token = F.runCancelable(effect)(IO.fromEither).unsafeRunSync()
        () => exec(token)
      }, Seq())
      storeState.map(state => shift(render(state, props)))
    } }

    def apply(props: Props): KeyAddingStage = impl(props)

    def subscribe: fs2.Stream[F, State]
    def render(state: State, props: Props): ReactElement
  }

  trait ContainerNoProps extends Container {
    def render(state: State): ReactElement

    type Props = Unit

    def apply(): KeyAddingStage = apply(())

    final override def render(state: State, props: Props): ReactElement =
      render(state)
  }

  trait ContainerNoState extends Container {
    def render(props: Props): ReactElement

    type State = Unit

    final override def subscribe: fs2.Stream[F, Unit] = fs2.Stream(())
    final override def render(state: Unit, props: Props): ReactElement = render(props)
  }
}
