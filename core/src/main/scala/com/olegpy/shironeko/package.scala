package com.olegpy

package object shironeko {
  type Store[S] = StoreK[[f[_]] =>> S]
}
