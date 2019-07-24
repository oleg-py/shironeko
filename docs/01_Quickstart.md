---
layout: docs
title: Quickstart
position: 1
---

If you don't have a slinky project yet, I recommend to start from the
official template:
```
$ sbt new shadaj/create-react-scala-app.g8
```

And add shironeko as a dependency:

```scala
//TODO: Not yet released
libraryDependencies += "com.olegpy" %%% "shironeko-slinky" % "0.1.0"
```

This will transitively pull shironeko-core, cats-effect and fs2. You
might want to specify concrete versions of those directly.

---

First, you need to describe an _algebra_ containing any data that needs
to be rendered on update. This algebra is similar in spirit to store in
Redux or circuit in Diode.

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

`Cell` here is merely an alias for `fs2.concurrent.SignallingRef`.

Our next step is to create a React component that would render that
state. To do it, we will need something called a `Connector`. Connector
links the algebra to a set of components that are going to use it.

```scala mdoc:js:shared
object Connector extends TaglessConnector[Algebra]
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


```scala
import scala.scalajs.js.annotation.JSExportTopLevel
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
  } >> IO.never // JS apps don't terminate normally
  
  // You will probably need this for webpack runner to run the app
  @JSExportTopLevel("main")
  def main(): Unit = super.main(Array())
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
- Integrating cats-effect actions with React
- Showing data from several sources in one component

Let's have 
- a counter with +/- buttons
- a name input
- current time display

in one component and one algebra. 

```scala mdoc:js:shared
import cats.effect._
import com.olegpy.shironeko.StoreDSL
import scalajs.js.Date
import scala.concurrent.duration.DurationInt


class NamedCounter[F[_]: Concurrent: Timer](dsl: StoreDSL[F]) {
  import dsl._
  
  val counter = cell(0)
  val name    = cell("")
  
  private[this] val getTimestamp = Sync[F].delay(new Date().toString)

  val timeStream = fs2.Stream.awakeEvery[F](1.second).evalMap(_ => getTimestamp)
}
```

We'll need a new connector for this algebra, too:

```scala mdoc:js:shared
object NameCountConnector extends TaglessConnector[NamedCounter]
```

Changing state is done by performing an action. An action in shironeko
is simply a value of type `F[Unit]`. This allows you to compose and
abstract over actions using familiar combinators from cats and
cats-effect.

While not necessary, it might be convenient to list actions in separate
classes/objects. Let's do this for counter-related actions but not for
name-related ones.

```scala mdoc:js:shared
object CounterActions {
  private def change[F[_]](by: Int)(implicit F: NamedCounter[F]) =
    F.counter.update(_ + by)
    
  def increment[F[_]: NamedCounter] = change(+1)
  def decrement[F[_]: NamedCounter] = change(-1)
}
```


To use these actions, we will need to convert them to plain slinky/react
callbacks. `Render` typeclass gives you limited FFI abilities in form
of `exec` method, which takes `F[Unit]` and schedules it for later time,
and `toCallback` method, that can convert actions or functions returning
them to impure callbacks that are needed for slinky.

Due to structure of shironeko containers, in tagless style FFI is not
available anywhere outside of `render` method.

```scala mdoc:js:shared
import com.olegpy.shironeko.util.combine
import scalajs.js.Dynamic.literal

object AdjustableCount extends NameCountConnector.ContainerNoProps {
  // You can implement `type State` with a case class
  case class State(timestamp: String, count: Int, name: String)
  
  def subscribe[F[_]: Subscribe]: fs2.Stream[F, State] = {
    val F = getAlgebra
    // `combine` macro takes a name of class, which companion has apply
    // method (e.g. tuple or case class) and builds a stream of values
    // of this class, built from the most recent values of each stream
    combine[State].from(
      F.timeStream,
      F.counter.discrete,
      F.name.discrete
    )
  }
  
  def render[F[_]: Render](s: State) = {
    div(
      div(s"Hello, ${if (s.name.isEmpty) "shironeko user" else s.name}!"),
      div(s"Current time: ${s.timestamp}"),
      div(
        // Here, toCallback converts action to () => Unit
        button(onClick := toCallback(CounterActions.decrement))("-"),
        span(style := literal(padding = "0 16px"), s"Current count is ${s.count}"),
        button(onClick := toCallback(CounterActions.increment))("+")
      ),
      div(
        "Your name is ",
        // Here, we use `exec` manually to trigger side-effects later
        input(`type` := "text", value := s.name, onChange := { e =>
          val newName = e.target.value
          exec(getAlgebra.name.set(newName))
        }),
        button(onClick := toCallback(getAlgebra.name.set("")))("Reset")
      )
    )
  }
}
```

The main object doesn't change much. Since `StoreDSL` provides impure
methods, it's constructor will return a `Resource` that will disable
those methods once it has been used up

```scala
import slinky.web.ReactDOM
import cats.effect.{IO, IOApp}
import org.scalajs.dom.document
import scala.scalajs.js.annotation.JSExportTopLevel

object TestCurrent extends IOApp {
  def run(args: List[String]) = {
    for {
      node <- IO { document.getElementById("root") }
      alg  <- StoreDSL[IO].use(dsl => IO.pure(new NamedCounter(dsl)))
      _    <- IO { ReactDOM.render(NameCountConnector(alg)(AdjustableCount()), node) }
    } yield ()
  } >> IO.never
}
```

```scala mdoc:js:invisible
import slinky.web.ReactDOM
import cats.effect.{IO, IOApp}

object TestCurrent extends IOApp {
  def run(args: List[String]) = {
    for {
      alg <- StoreDSL[IO].use(dsl => IO.pure(new NamedCounter(dsl)))
      _   <- IO { ReactDOM.render(NameCountConnector(alg)(AdjustableCount()), node) }
    } yield ()
  } >> IO.never
}
TestCurrent.main(Array())
```

You can use containers inside other containers, as well as mix them with
regular Slinky components as you wish. The only restriction is that
`Connector.apply` is called higher in rendering tree than any of its'
containers.
