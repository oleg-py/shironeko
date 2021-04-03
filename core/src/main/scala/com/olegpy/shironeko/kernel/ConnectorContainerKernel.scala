package com.olegpy.shironeko.kernel

import cats.effect.Resource
import com.olegpy.shironeko.{Later, StoreK}


trait ConnectorContainerKernel[F[_], R] {
  type Container[P]
  type Connector

  def mount[P](connector: Connector, container: Container[P], props: P, fold: Later[R] => R): R

  def shareStoreK[S[_[_]]](
    connector: Connector,
    store: Resource[F, S[F]]
  )(implicit S: StoreK[S]): F[Resource[F, S[F]]]

  def lookupStoreK[S[_[_]]](connector: Connector)(implicit S: StoreK[S]): F[Option[Resource[F, S[F]]]]
}
