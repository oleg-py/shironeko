package com.olegpy.shironeko

import cats.effect.{ConcurrentEffect, IO}
import slinky.core.{FunctionalComponent, KeyAddingStage}
import slinky.core.facade.{Hooks, React, ReactElement}
import cats.implicits._
import fs2.Pure

class SlinkyConnector[F[_], Algebra] {
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

  trait Container {
    type State
    type Props

    final protected def exec(action: F[Unit]): Unit =
      F.runAsync(action)(IO.fromEither).unsafeRunSync()

    private[this] val impl = FunctionalComponent[Props] { props =>
      val self: Algebra = Hooks.useContext(ctx)
      val (storeState, setStoreState) = Hooks.useState(none[State])
      Hooks.useEffect(() => {
        implicit val ce: ConcurrentEffect[F] = F
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

  trait ContainerNoState extends Container {
    def render(props: Props)(implicit F: Algebra): ReactElement

    type State = Unit
    final override def subscribe(implicit F: Algebra): fs2.Stream[Pure, Unit] =
      fs2.Stream(())

    final override def render(state: Unit, props: Props)(implicit F: Algebra): ReactElement =
      render(props)
  }
}
