package com.olegpy.shironeko

import cats.effect.{Concurrent, Resource, Sync, SyncIO}
import fs2.concurrent.{SignallingRef, Topic}


trait StoreDSL[F[_]] {
  def cell[A](initial: A): Cell[F, A]
  def events[A]: Events[F, A]
}

object StoreDSL {
  def apply[F[_]: Concurrent]: Resource[F, StoreDSL[F]] = Resource {
    Sync[F].delay {
      var isDone = false
      def guard[A](block: => A) =
        if (!isDone) block
        else sys.error("Attempt to use an instance of UnsafeDSL out of its scope")

      val dsl = new StoreDSL[F] {
        def cell[A](initial: A): Cell[F, A] = guard {
          SignallingRef.in[SyncIO, F, A](initial).unsafeRunSync()
        }

        def events[A]: Events[F, A] = guard {
          Topic.in[SyncIO, F, Option[A]](None).unsafeRunSync()
        }
      }

      (dsl, Sync[F].delay { isDone = true })
    }
  }
}
