package com.olegpy.shironeko

import cats.effect.{Concurrent, ConcurrentEffect, IO}
import slinky.core.{FunctionalComponent, FunctionalComponentName, KeyAddingStage}
import slinky.core.facade.{Hooks, React, ReactElement}
import cats.implicits._
import com.olegpy.shironeko.interop.Exec


class TaglessConnector[Algebra[_[_]]] { conn =>
  def reportUncaughtException(e: Throwable): Unit = e.printStackTrace()

  sealed trait SubscribeM {
    type f[_]
    def algebra: Algebra[f]
    def concurrent: Concurrent[f]
  }

  sealed trait RenderM extends SubscribeM {
    def exec: Exec[f]
  }

  type Subscribe[F[_]] = SubscribeM { type f[a] = F[a] }
  type Render[F[_]] = RenderM { type f[a] = F[a] }

  private[shironeko] class RenderInstance[F[_]](
    override val algebra: Algebra[F],
    override val concurrent: ConcurrentEffect[F],
  ) extends RenderM {
    type f[a] = F[a]
    override val exec: Exec[f] = Exec.fromEffect(concurrent)
  }

  trait RenderHelpers {
    // Copying this over, for ergonomics
    final type Subscribe[f[_]] = conn.Subscribe[f]
    final type Render[f[_]] = conn.Render[f]

    protected implicit def getAlgebra[F[_]: Subscribe]: Algebra[F] =
      implicitly[Subscribe[F]].algebra

    protected implicit def getConcurrent[F[_]: Subscribe]: Concurrent[F] =
      implicitly[Subscribe[F]].concurrent

    protected implicit def getExec[F[_]: Render]: Exec[F] =
      implicitly[Render[F]].exec
  }

  private[this] val ctx = React.createContext(null: RenderM)

  private[this] val providerFunc =
    FunctionalComponent[(RenderM, ReactElement)] {
      case (render, elem) => ctx.Provider(render)(elem)
    }(new FunctionalComponentName(conn.getClass.getSimpleName))

  def apply[F[_]](elem: ReactElement)(implicit F: ConcurrentEffect[F], store: Algebra[F]): ReactElement =
    apply(store)(elem)(F)

  def apply[F[_]: ConcurrentEffect](store: Algebra[F])(elem: ReactElement): ReactElement =
    providerFunc(new RenderInstance[F](store, ConcurrentEffect[F]) -> elem)


  trait ContainerF extends Exec.Boilerplate with RenderHelpers {
    type State[F[_]]
    type Props

    protected def getClassName = this.getClass.getSimpleName

    lazy val fcName = new FunctionalComponentName(getClassName)

    @inline private[this] def unsafeRun[Z[_]: RenderInstance](effect: Z[Unit]) = {
      implicitly[RenderInstance[Z]].concurrent.runCancelable(effect) {
        case Left(ex) => IO(reportUncaughtException(ex))
        case Right(value) => IO.pure(value)
      }.unsafeRunSync()
    }

    private[this] lazy val impl = FunctionalComponent[Props](props => {
      val ctxVal = Hooks.useContext(ctx)
      if (ctxVal == null) {
        throw new IllegalStateException(
          s"ConcurrentEffect instance was not provided to component. Make sure ${conn.getClass.getName}.apply is called above every container"
        )
      }

      implicit val renderZ: RenderInstance[ctxVal.f] = ctxVal match {
        case ri: RenderInstance[ctxVal.f] => ri
      }

      type Z[a] = ctxVal.f[a]

      val (state, setState) = Hooks.useState(none[State[Z]])

      Hooks.useLayoutEffect(() => {
        val token = unsafeRun {
          subscribe[Z].evalMap(s => renderZ.concurrent.delay {
            setState(s.some)
          }).compile.drain
        }
        () => { unsafeRun(token); () }
      }, Seq(ctxVal))

      state.map(state => render[Z](state, props))
    })(fcName)

    def apply(props: Props): KeyAddingStage = impl(props)

    def subscribe[F[_]: Subscribe]: fs2.Stream[F, State[F]]
    def render[F[_]: Render](state: State[F], props: Props): ReactElement
  }

  trait ContainerFNoProps extends ContainerF {
    def render[F[_]: Render](state: State[F]): ReactElement

    type Props = Unit
    def apply(): KeyAddingStage = apply(())
    final def render[F[_]: Render](state: State[F], props: Unit): ReactElement =
      render(state)
  }

  trait Container extends Exec.Boilerplate with RenderHelpers { self =>
    type State
    type Props

    protected def getClassName = self.getClass.getSimpleName

    private[this] object underlying extends ContainerF {
      type State[F[_]] = self.State
      type Props = self.Props

      override protected def getClassName = self.getClassName

      def subscribe[F[_]: Subscribe] = self.subscribe[F]
      def render[F[_]: Render](state: State[F], props: Props) =
        self.render[F](state, props)
    }

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
    final override def subscribe[F[_]: Subscribe]: fs2.Stream[F, Unit] =
      fs2.Stream(())

    final override def render[F[_]: Render](state: Unit, props: Props): ReactElement =
      render(props)
  }
}
