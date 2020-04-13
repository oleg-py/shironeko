package com.olegpy

import fs2.concurrent.SignallingRef

package object shironeko {
  type Cell[F[_], A] = SignallingRef[F, A]
  val  Cell          = SignallingRef
}
