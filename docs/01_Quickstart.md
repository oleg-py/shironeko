---
layout: docs
title: Quickstart
position: 1
---

First, describe an _algebra_ containing any data that needs to be
rendered on update. This algebra is similar in spirit to store in Redux
or Circuit+Model in Diode.

For the sake of example, let's implement a simple
component that will update itself periodically:

```scala mdoc:js:shared
import com.olegpy.shironeko._
import cats.implicits._
import cats.effect.Concurrent

class Algebra[F[_]](val count: Cell[F, Int])

object Algebra {
  def create[F[_]: Concurrent] = Cell[F, Int](0).map(new Algebra(_))
}
```

Cell here is merely an alias for `fs2.concurrent.SignallingRef`.

Our next step is to create a React component that would render that
state. To do it, we will need something called a `Connector`. Connector
links the algebra to a set of components that are going to use it.

```scala mdoc:js:shared
object Connector extends SlinkyConnector[Algebra]
```

`Connector` contains several base classes for components that link 
algebra to React elements.

```scala mdoc:js:shared
import slinky.web.html._

object CurrentCount extends Connector.ContainerNoProps {
  type State = Int
  def subscribe[F[_]: Subscribe] = getAlgebra.count.discrete
  def render[F[_]: Render](state: Int) = 
    div(s"Current count: $state")
}
```

To actually link components with algebra, you need a `ConcurrentEffect`
instance where you do it. To see that it actually refreshes, let's modify
the counter every 3 seconds with a simple monadic loop:


```scala mdoc:js
import slinky.web.ReactDOM
import cats.effect.{IO, IOApp}
import org.scalajs.dom.document
import scala.concurrent.duration._

object TestCurrent extends IOApp {
  def findNode = IO { document.getElementById("root") }
  def updateCount(alg: Algebra[IO]) = {
    val nap = IO.sleep(3.seconds)
    val operate = alg.count.set(-1) >> nap >> alg.count.set(1) >> nap
    operate.foreverM
  }

  def run(args: List[String]) = {
    for {
      alg  <- Algebra.create[IO]
      _    <- alg.count.set(10)
      node <- findNode
      _    <- IO { ReactDOM.render(Connector(alg)(CurrentCount()), node) }
      _    <- updateCount(alg).start
    } yield ()
  } >> IO.never
}
```

Assuming the project is properly set up with slinky-webpack, you should
be able to use `dev` command to run a server and see your element being
updated every 3 seconds:

```scala mdoc:js:invisible
// copying this to make node visible
import slinky.web.ReactDOM
import cats.effect.{IO, IOApp}
import scala.concurrent.duration._

object TestCurrent extends IOApp {
  def updateCount(alg: Algebra[IO]) = {
    val nap = IO.sleep(3.seconds)
    val operate = alg.count.set(-1) >> nap >> alg.count.set(1) >> nap
    operate.foreverM
  }
  def run(args: List[String]) = {
    for {
      alg <- Algebra.create[IO]
      _   <- IO { ReactDOM.render(Connector(alg)(CurrentCount()), node) }
      _    <- updateCount(alg).start
    } yield ()
  } >> IO.never
}
TestCurrent.main(Array())
```

---

Ok, now for something more serious:
-- TODO!