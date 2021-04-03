package com.olegpy.shironeko


object Store {
  trait Companion[S] extends StoreK.Companion[λ[f[_] => S]]
}