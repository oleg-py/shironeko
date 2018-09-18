package com.olegpy.shironeko

import fs2.Stream
import cats.effect.concurrent.Ref

trait Cell[F[_], A] extends Ref[F, A] {
  def listen: Stream[F, A]
}
