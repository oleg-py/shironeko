package com.olegpy.shironeko

package object kernel {
  type SomeEffectType[A]

  def unsafeSubst[U[_[_]], F[_]](u: U[SomeEffectType]): U[F] =
    u.asInstanceOf[U[F]]
}
