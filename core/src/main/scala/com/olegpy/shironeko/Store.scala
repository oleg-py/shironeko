package com.olegpy.shironeko


object Store {
  trait Companion[S] extends StoreK.Companion[[f[_]] =>> S]
}