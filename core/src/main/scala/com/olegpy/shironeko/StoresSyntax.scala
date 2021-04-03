package com.olegpy.shironeko

import cats.effect.MonadCancel
import cats.effect.kernel.Resource


trait StoresSyntax {
  implicit class CompanionKOps[S[_[_]]](private val S: StoreK.Companion[S]) {
    def link[F[_]: Stores]: Resource[F, S[F]] = Stores[F].linkF(S.storeInstance)
    def linkS[F[_]: Stores](implicit F: MonadCancel[F, _]): fs2.Stream[F, S[F]] =
      fs2.Stream.resource(link[F])
  }
}
