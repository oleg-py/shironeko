package com.olegpy.shironeko.kernel

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal
class WeakMap[K, V]() extends js.Object {
  def get(key: K): js.UndefOr[V] = js.native
  def set(key: K, value: V): this.type = js.native
  def has(key: K): Boolean = js.native
  def delete(key: K): Boolean = js.native
}
