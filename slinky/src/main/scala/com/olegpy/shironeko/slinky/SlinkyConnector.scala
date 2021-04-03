package com.olegpy.shironeko.slinky

import scala.scalajs.js

import _root_.slinky.core.{BuildingComponent, FunctionalComponent}
import _root_.slinky.core.facade.{React, ReactContext, ReactElement}
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.Dispatcher
import cats.implicits._
import com.olegpy.shironeko.{Store, StoreK, Stores}
import com.olegpy.shironeko.interop.Exec
import com.olegpy.shironeko.kernel.{ResourcePool, SomeEffectType, WeakMap, unsafeSubst}
import com.olegpy.shironeko.slinky.SlinkyConnector.globalCtx


class SlinkyConnector[F[_]: Async](
  private[slinky] val dispatcher: Dispatcher[F],
  private[slinky] val pool: ResourcePool[F],
) extends Exec[F] with Stores[F] {
  val asyncInstance: Async[F] = Async[F]

  override def unsafeRunLater[A](fa: F[A]): Unit = dispatcher.unsafeRunAndForget(fa)

  override def linkF[S[_[_]]](implicit S: StoreK[S]): Resource[F, S[F]] =
    Resource.eval {
      SlinkyKernel[F].lookupStoreK(this).flatMap(_.liftTo[F](
        new Exception(s"${S.name} resource is not registered in the connector"))
      )
    }.flatten


  def regStoreK[S[_[_]]: StoreK](allocate: Resource[F, S[F]]): F[Resource[F, S[F]]] =
    SlinkyKernel[F].shareStoreK(this, allocate)

  def regStore[S: Store](allocate: Resource[F, S]): F[Resource[F, S]] =
    regStoreK[λ[f[_] => S]](allocate)


  def Provider: BuildingComponent[Nothing, js.Object] =
    SlinkyConnector.globalCtxIn[F].Provider(js.defined(this))

  private[shironeko] val weakMap =
    new WeakMap[BaseSlinkyContainer[F], FunctionalComponent[_]]
}

object SlinkyConnector {
  private[shironeko] val globalCtx =
    React.createContext[js.UndefOr[SlinkyConnector[SomeEffectType]]](js.undefined)

  private def globalCtxIn[F[_]] =
    unsafeSubst[λ[f[_] => ReactContext[js.UndefOr[SlinkyConnector[f]]]], F](globalCtx)

  def make[F[_]: Async]: Resource[F, SlinkyConnector[F]] =
    (Dispatcher[F], ResourcePool[F]).mapN(new SlinkyConnector(_, _))
}