package com.olegpy.shironeko.slinky

import scala.concurrent.{ExecutionContext, Promise}

import cats.effect.Resource
import cats.effect.kernel.Async
import cats.effect.std.Queue
import cats.implicits._
import com.olegpy.shironeko.{Later, StoreK}
import com.olegpy.shironeko.kernel.{ConnectorContainerKernel, SomeEffectType, unsafeSubst}
import slinky.core.FunctionalComponent
import slinky.core.facade.{Hooks, ReactElement}


class SlinkyKernel[F[_]] extends ConnectorContainerKernel[F, ReactElement] {
  override final type Container[P] = BaseSlinkyContainer[F] { type Props = P }
  override final type Connector = SlinkyConnector[F]

  override def mount[P](connector: Connector, container: Container[P], props: P, fold: Later[ReactElement] => ReactElement): ReactElement = {
    def buildFC = {
      implicit val F: Async[F] = connector.asyncInstance

      FunctionalComponent[(P, Later.Fold[ReactElement])] { case (props, fold) =>
        val (state, setState) = Hooks.useState[Later[container.PrerenderState]](Later.Loading)
        val onProps = Hooks.useMemo(() => Promise[P => Unit](), Seq())


        Hooks.useLayoutEffect(() => {
          val feed = for {
            queue <- Queue.circularBuffer[F, P](1)
            unsafeOffer = (queue.offer _) andThen connector.dispatcher.unsafeRunAndForget
            _ <- Async[F].delay(onProps.success(unsafeOffer))
            _ <- container.prerender(connector, fs2.Stream.repeatEval(queue.take))
              .map(Later.Ready(_))
              .handleErrorWith(e => fs2.Stream(Later.Failed(e)))
              .evalMap(r => F.delay(setState(r)))
              .compile.drain
          } yield ()
          val cancel = connector.dispatcher.unsafeRunCancelable(feed)
          cancel.void
        }, Seq())

        Hooks.useLayoutEffect(() => {
          onProps.future.foreach(_(props))(ExecutionContext.parasitic)
        }, Seq(props))
        fold(state.map(container.rawRender(connector, _)))
      }
    }

    def mountAsFC: FunctionalComponent[(P, Later.Fold[ReactElement])] =
      connector.weakMap.get(container).getOrElse {
        val fc = buildFC
        connector.weakMap.set(container, fc)
        fc
      }.asInstanceOf[FunctionalComponent[(P, Later.Fold[ReactElement])]]

    mountAsFC((props, fold))
  }

  override def shareStoreK[S[_[_]]](connector: SlinkyConnector[F], store: Resource[F, S[F]])(implicit S: StoreK[S]): F[Resource[F, S[F]]] = {
    implicit val F: Async[F] = connector.asyncInstance
    connector.pool.register(S.resourceKey[F], store, S.storeAcquisitionPolicy) >>
    connector.pool.lookup(S.resourceKey[F]).flatMap {
      _.liftTo[F](new IllegalStateException("Resource not in pool after registering"))
    }
  }

  override def lookupStoreK[S[_[_]]](connector: SlinkyConnector[F])(implicit S: StoreK[S]): F[Option[Resource[F, S[F]]]] =
    connector.pool.lookup(S.resourceKey[F])
}

object SlinkyKernel {
  def apply[F[_]]: SlinkyKernel[F] = unsafeSubst(reusable)

  private val reusable = new SlinkyKernel[SomeEffectType]

  val showNothingUntilReady: Later.Fold[ReactElement] = {
    case Later.Loading => null
    case Later.Failed(ex) => throw ex
    case Later.Ready(a) => a
  }
}