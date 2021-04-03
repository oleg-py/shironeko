package com.olegpy.shironeko.slinky

import scala.scalajs.js

import _root_.slinky.core.FunctionalComponent
import _root_.slinky.core.facade.React
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.Dispatcher
import cats.implicits._
import com.olegpy.shironeko.{StoreK, Stores}
import com.olegpy.shironeko.interop.Exec
import com.olegpy.shironeko.kernel.{ResourcePool, SomeEffectType, WeakMap}


class SlinkyConnector[F[_]: Async](
  private[slinky] val dispatcher: Dispatcher[F],
  private[slinky] val pool: ResourcePool[F],
) extends Exec[F] with Stores[F] {
  val asyncInstance: Async[F] = Async[F]

  override def unsafeRunLater[A](fa: F[A]): Unit = dispatcher.unsafeRunAndForget(fa)

  override def linkF[S[_[_]]](implicit S: StoreK[S]): Resource[F, S[F]] =
    Resource.eval {
      pool.lookup(S.resourceKey[F]).flatMap(_.liftTo[F](
        new Exception(s"${S.name} resource is not registered in the connector"))
      )
    }.flatten

  private[shironeko] val weakMap =
    new WeakMap[BaseSlinkyContainer[F], FunctionalComponent[_]]
}

object SlinkyConnector {
  private[shironeko] val globalCtx =
    React.createContext[js.UndefOr[SlinkyConnector[SomeEffectType]]](js.undefined)
}