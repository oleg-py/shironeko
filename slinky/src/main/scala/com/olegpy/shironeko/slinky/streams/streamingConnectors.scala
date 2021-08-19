package com.olegpy.shironeko.slinky.streams

import cats.effect.Temporal
import com.olegpy.shironeko.{Stores, StoresSyntax}
import com.olegpy.shironeko.interop.Exec
import com.olegpy.shironeko.kernel.SomeEffectType
import com.olegpy.shironeko.slinky.{BaseSlinkyContainer, SlinkyConnector}
import slinky.core.facade.ReactElement


trait StreamingSlinkyContainer[F[_]] extends BaseSlinkyContainer[F] with Exec.Boilerplate with StoresSyntax {
  final type PrerenderState = ReactElement
  override def rawRender(conn: SlinkyConnector[F], ps: ReactElement): ReactElement = ps
}

trait SlinkyContainer[F[_]] extends StreamingSlinkyContainer[F] {
  type Props

  def render(props: fs2.Stream[F, Props])(implicit F: Exec[F], S: Stores[F]): fs2.Stream[F, ReactElement]

  override final def prerender(conn: SlinkyConnector[F], props: fs2.Stream[F, Props]): fs2.Stream[F, ReactElement] = {
    render(props)(conn, conn)
  }
}

object SlinkyContainer {
  trait NoProps[F[_]] extends SlinkyContainer[F] {
    override final type Props = Unit

    def render(implicit F: Exec[F], S: Stores[F]): fs2.Stream[F, ReactElement]

    override final def render(props: fs2.Stream[F, Unit])(implicit F: Exec[F], S: Stores[F]): fs2.Stream[F, ReactElement] =
      render(F, S)
  }
}

trait SlinkyContainerK extends StreamingSlinkyContainer[SomeEffectType] {
  type Props

  def render[F[_]: Temporal: Exec: Stores](props: fs2.Stream[F, Props]): fs2.Stream[F, ReactElement]

  final override def prerender(conn: SlinkyConnector[SomeEffectType], props: fs2.Stream[SomeEffectType, Props]): fs2.Stream[SomeEffectType, ReactElement] =
    render[SomeEffectType](props)(conn.asyncInstance, conn, conn)
}

object SlinkyContainerK {
  trait NoProps extends SlinkyContainerK {
    override final type Props = Unit

    def render[F[_]: Temporal: Exec: Stores]: fs2.Stream[F, ReactElement]

    final override def render[F[_] : Temporal : Exec: Stores](props: fs2.Stream[F, Unit]): fs2.Stream[F, ReactElement] =
      render[F]
  }
}