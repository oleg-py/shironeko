package com.olegpy.shironeko

import cats.effect.kernel.Resource


trait Stores[F[_]] {
  def linkF[S[_[_]]](implicit S: StoreK[S]): Resource[F, S[F]]
}

object Stores {
  def apply[F[_]](implicit F: Stores[F]): F.type = F
}