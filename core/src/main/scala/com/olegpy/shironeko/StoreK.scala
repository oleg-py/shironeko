package com.olegpy.shironeko

import cats.effect.kernel.Unique
import com.olegpy.shironeko.kernel.{ResourcePool, SomeEffectType, unsafeSubst}


trait StoreK[R[_[_]]] {
  def name: String
  def resourceKey[F[_]]: ResourcePool.Key[R[F]]
  def storeAcquisitionPolicy: ResourcePool.Policy
}

object StoreK {
  trait Companion[R[_[_]]] { self =>
    implicit val storeInstance: StoreK[R] = new StoreK[R] {
      override def name: String = self.name

      override def resourceKey[F[_]]: ResourcePool.Key[R[F]] =
        unsafeSubst[[f[_]] =>> ResourcePool.Key[R[f]], F](unsafeKey)

      override def storeAcquisitionPolicy: ResourcePool.Policy = self.storeAcquisitionPolicy
    }

    def storeAcquisitionPolicy: ResourcePool.Policy =
      ResourcePool.Policy(
        ResourcePool.Finalization.Immediately,
        ResourcePool.Reacquisition.Synchronously
      )

    private[this] lazy val name = this.getClass.getSimpleName
    private[this] val unsafeKey = ResourcePool.Key[R[SomeEffectType]](new Unique.Token)
  }
}