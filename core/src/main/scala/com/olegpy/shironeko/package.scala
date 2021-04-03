package com.olegpy

package object shironeko {
  type Store[S] = StoreK[λ[f[_] => S]]
}
