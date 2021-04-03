package com.olegpy.shironeko.slinky

import com.olegpy.shironeko.kernel.unsafeSubst
import com.olegpy.shironeko.Later
import slinky.core.facade.ReactElement


trait BaseSlinkyContainer[F[_]] {
  type Props
  type PrerenderState

  def prerender(conn: SlinkyConnector[F], props: fs2.Stream[F, Props]): fs2.Stream[F, PrerenderState]
  def rawRender(conn: SlinkyConnector[F], ps: PrerenderState): ReactElement

  final def apply(props: Props): ReactElement =
    renderHere(props)(SlinkyKernel.showNothingUntilReady)

  final def renderHere(props: Props)(fold: Later.Fold[ReactElement]): ReactElement = {
    SlinkyConnector.globalCtx.Consumer { ctx =>
      val conn = unsafeSubst[SlinkyConnector, F] {
        ctx.getOrElse(sys.error("Slinky container mounted without a connector"))
      }
      SlinkyKernel[F].mount[Props](conn, this, props, fold)
    }
  }
}

