package com.olegpy.shironeko.util

import scala.collection.mutable


class Cache {
  private[this] val map = mutable.AnyRefMap[Cache.Id, Cache.Val]()

  def apply[A](a: => A)(implicit pos: CallPosition): A =
    dependent(null)(a)

  def apply[A](key: String)(a: => A)(implicit pos: CallPosition): A =
    dependent(key)(a)

  def dependent[A](key: String, deps: Seq[Any] = null)(a: => A)(implicit pos: CallPosition): A = {
    val id = Cache.Id(pos, key)
    map.get(id) match {
      case Some(value) if value.deps == deps =>
        value.get.asInstanceOf[A]
      case None =>
        val result = a
        map.update(id, Cache.Val(result, deps))
        result
    }
  }
}

object Cache {
  private[Cache] case class Id (
    callPos: CallPosition,
    key: String = null
  )

  private[Cache] case class Val(
    get: Any,
    deps: Seq[_] = null
  )
}