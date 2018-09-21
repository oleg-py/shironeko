package com.olegpy.shironeko

trait ImpureIntegration[F[_]] { this: StoreBase[F] =>
  def exec(a: Action): Unit = F.toIO(a).unsafeRunAsyncAndForget()
  def execS(f: this.type => Action): Unit = exec(f(this))
}