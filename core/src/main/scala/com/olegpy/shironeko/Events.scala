package com.olegpy.shironeko

import fs2.{Pipe, Stream}


trait Events[F[_], A] {
  def emit: Pipe[F, A, Unit]
  def emit1(a: A): F[Unit]
  def listen: Stream[F, A]
  def await1[B](pf: PartialFunction[A, B]): F[B]
}
