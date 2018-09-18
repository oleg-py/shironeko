package com.olegpy.shironeko

import fs2.{Sink, Stream}


trait Events[F[_], A] {
  def emit: Sink[F, A]
  def emit1(a: A): F[Unit]
  def listen: Stream[F, A]
  def await1[B](pf: PartialFunction[A, B]): F[B]
}
