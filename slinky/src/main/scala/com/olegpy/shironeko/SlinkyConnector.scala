package com.olegpy.shironeko

import cats.effect.{ConcurrentEffect, IO}
import slinky.core.{FunctionalComponent, KeyAddingStage}
import slinky.core.facade.{Hooks, React, ReactElement}
import cats.implicits._

class SlinkyConnector[F[_], Algebra] {
  private[this] val ctx = React.createContext(null.asInstanceOf[Algebra])
  private[this] var F: ConcurrentEffect[F] = _

  private[this] val providerFunc =
    FunctionalComponent[(Algebra, ConcurrentEffect[F], ReactElement)] {
      case (alg, f, elem) =>
        Hooks.useEffect(() => {
          F = f
          () => { F = null }
        }, Seq(f))
        ctx.Provider(alg)(elem)
    }

  def apply(store: Algebra)(elem: ReactElement)(implicit F: ConcurrentEffect[F]): ReactElement =
    providerFunc((store, F, elem))

  trait Container {
    type State
    type Props

    final protected def exec(action: F[Unit]): Unit =
      F.runAsync(action)(IO.fromEither).unsafeRunSync()

    private[this] val impl = FunctionalComponent[Props] { props =>
      implicit val ce: ConcurrentEffect[F] = F
      val self: Algebra = Hooks.useContext(ctx)
      val (storeState, setStoreState) = Hooks.useState(none[State])
      Hooks.useEffect(() => {
        val effect = subscribe(self)
          .evalMap { e => F.delay(setStoreState(e.some))}
          .compile.drain

        val token = F.runCancelable(effect)(IO.fromEither).unsafeRunSync()
        () => exec(token)
      }, Seq())
      storeState.map(render(_, props)(self))
    }

    def apply(props: Props): KeyAddingStage = impl(props)

    def subscribe(implicit F: Algebra): fs2.Stream[F, State]
    def render(state: State, props: Props)(implicit F: Algebra): ReactElement
  }

  trait ContainerNoProps extends Container {
    final type Props = Unit
    def apply(): KeyAddingStage = apply(())
    def render(state: State)(implicit F: Algebra): ReactElement

    final def render(state: State, props: Unit)(implicit F: Algebra): ReactElement =
      render(state)
  }
}
