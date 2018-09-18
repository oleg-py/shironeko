package com.olegpy.shironeko.internals

import com.olegpy.shironeko.StoreBase
import cats.effect.implicits._


trait Preloading[F[_]] { this: StoreBase[F] =>
  protected def preload[A](fa: F[A]): F[A] =
    fa.toIO.start.unsafeRunSync().join.to[F]
}
