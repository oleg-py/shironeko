package com.olegpy.shironeko

import cats.Functor


sealed trait Later[+A]
object Later {
  case object Loading extends Later[Nothing]
  case class Failed(ex: Throwable) extends Later[Nothing]
  case class Ready[A](a: A) extends Later[A]

  type Fold[R] = Later[R] => R

  implicit val functor: Functor[Later] = new Functor[Later] {
    override def map[A, B](fa: Later[A])(f: A => B): Later[B] =
      fa match {
        case Ready(a) => Ready(f(a))
        case other: Later[Nothing @unchecked] => other
      }
  }
}
