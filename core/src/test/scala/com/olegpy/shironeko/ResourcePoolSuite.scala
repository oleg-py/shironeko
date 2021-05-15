package com.olegpy.shironeko

import cats.implicits._
import cats.data.Chain
import cats.effect.{IO, Ref, Resource}
import com.olegpy.shironeko.kernel.ResourcePool
import weaver.SimpleIOSuite

import scala.concurrent.duration._

object ResourcePoolSuite extends SimpleIOSuite {

  test("ResourcePool closes constituent resources on finalization") {
    setupPool.use { pool =>
      pool.lookup(Tracer.storeInstance.resourceKey[IO])
        .flatMap(_.traverse(_.use(_.ref.pure[IO])))
    }
      .flatMap(_.traverse(_.get))
      .map(_.orEmpty)
      .map { ops =>
        expect.same(null, ops) // FIXME deadlock on finalizer
      }
  }

  def setupPool =
    ResourcePool[IO]
      .evalTap { pool => pool.autoReg(Tracer.make) }
      .evalTap { pool =>
        pool.lookup(Tracer.storeInstance.resourceKey[IO]).map(_.get).flatMap { tRsc =>
          pool.autoReg(tRsc.flatMap(R1.make)) >>
            pool.autoReg(tRsc.flatMap(R2.make)) >>
            pool.autoReg(tRsc.flatMap(R3.make)) >>
            pool.autoReg(tRsc.flatMap(R4.make))
        }
      }

  implicit class PoolOps(self: ResourcePool[IO]) {
    def autoReg[A](r: Resource[IO, A])(implicit A: Store[A]) =
      self.register(A.resourceKey[IO], r, A.storeAcquisitionPolicy)
  }

  case class Tracer(ref: Ref[IO, Chain[Op]])
  object Tracer extends Store.Companion[Tracer] {
    def make: Resource[IO, Tracer] =
      Resource.eval(Ref[IO].of(Chain.empty[Op])) map Tracer.apply
  }

  sealed trait Op
  case class Allocate(r: Rsc) extends Op
  case class Deallocate(r: Rsc) extends Op

  sealed trait Rsc
  sealed trait Factory[A] extends Rsc { self: A =>
    def make(t: Tracer): Resource[IO, A] =
      Resource.make(t.ref.update(_ :+ Allocate(this)) as this) { _ =>
        IO.println(s"Deallocating ${this}") >>
        IO.sleep(1.second) >> t.ref.update(_ :+ Deallocate(this))
      }
  }

  sealed trait R1 extends Factory[R1]
  object R1 extends Store.Companion[R1] with R1 {
    override def storeAcquisitionPolicy: ResourcePool.Policy =
      ResourcePool.Policy(
        ResourcePool.Finalization.Immediately,
        ResourcePool.Reacquisition.Synchronously,
      )
  }

  sealed trait R2 extends Factory[R2]
  object R2 extends Store.Companion[R2] with R2 {
    override def storeAcquisitionPolicy: ResourcePool.Policy =
      ResourcePool.Policy(
        ResourcePool.Finalization.Immediately,
        ResourcePool.Reacquisition.Concurrently,
      )
  }

  sealed trait R3 extends Factory[R3]
  object R3 extends Store.Companion[R3] with R3 {
    override def storeAcquisitionPolicy: ResourcePool.Policy =
      ResourcePool.Policy(
        ResourcePool.Finalization.Never,
        ResourcePool.Reacquisition.Synchronously
      )
  }

  sealed trait R4 extends Factory[R4]
  object R4 extends Store.Companion[R4] with R4 {
    override def storeAcquisitionPolicy: ResourcePool.Policy =
      ResourcePool.Policy(
        ResourcePool.Finalization.After(10.seconds),
        ResourcePool.Reacquisition.Concurrently,
      )
  }
}
