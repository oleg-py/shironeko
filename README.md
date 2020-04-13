# Shironeko
![Maven central](https://img.shields.io/maven-central/v/com.olegpy/shironeko-core_2.12.svg?style=flat-square)

*A cat that can manage state*

Shironeko is a state management library for Scala.js with the following goals:

- Make simple things trivial and hard things possible without boilerplate
- Support writing logic in pure FP, with cats-effect and final tagless style
- Be developer and IDE-friendly

Currently only supports Scala 2.12

# Dependencies

```scala
libraryDependencies += "com.olegpy" %%% "shironeko-core" % "0.1.0-M1"
libraryDependencies += "com.olegpy" %%% "shironeko-slinky" % "0.1.0-M1"
```

# Core abstractions

shironeko is largely relying on cats-effect and fs2. Every action that
happens is represented by `F[Unit]` (for cats-effect compatible effect
type `F`). All data that is rendered comes in `fs2.Stream`. For state
cells that can be changed manually, `SignallingRef[F, A]` is used, as it
provides a stream of changed values via `.discrete` method.

- `Store` is a class containing the data your application is showing
- `Container` is a (react) component which is able to show data from the
   Store and has FFI compatibilities for react interop
- `Connector` is an object which links Containers to an instance of a Store

# Example
## Basics
Let's say we'll be using tagless final style. We want to create a simple
counter which you can increment or decrement, so we keep that state in
a store:

```scala
class Store[F[_]](val counter: SignallingRef[F, Int])
object Store {
  def make[F[_]: Concurrent] = SignallingRef[F, Int](0).map(new Store[F](_))
}
```

To get any updates, we need to first create a `Connector` for our
application.
```scala
object Connector extends SlinkyConnector[Store]
```

Connectors define a number of base classes to be extended by other
singleton objects. Here, let's use a simple container without any props:

```scala
object CounterDisplay extends Connector.ContainerNoProps {
  override type State = Int
  
  override def subscribe[F[_]: Subscribe] = getAlgebra.counter.discrete
  override def render[F[_]: Render](state: State) = {
    div(
      button(onClick := toCallback { getAlgebra.counter.modify(_ - 1) })("-"),
      s"Current value is $state",
      button(onClick := toCallback { getAlgebra.counter.modify(_ + 1) })("+"),
    )
  }
}
```

Extending any container class gives access to the:
- Store instance (`getAlgebra`) for `F` effect type in subscribe and render
- Concurrent instance (`getConcurrent`) for `F` effect type in subscribe and render
- FFI type `Exec` (`getExec`) for `F` in render only, for tagless style, or
  in both render and subscribe when using concrete effect type.
  
`Exec` allows you to use `exec(fa)` to schedule `fa` for later execution,
and also `toCallback` utility, converting `fa` to impure callback (`() => Unit`)

With this, we have enough to build our app. I will be
using `cats.effect.IO` as effect type, and the easiest way to get all
needed typeclass instances is by extending `IOApp`:

```scala
object Main extends IOApp {
  override def run(args:  List[String])  = {
    Store.make[IO].flatMap(store => IO.suspend { 
      val root = dom.document.getElementById("root")
      ReactDOM.render(root, Connector(store)(CounterDisplay()))
      IO.never.widen[ExitCode]
    })
  }
  
  @JSExportTopLevel("main")
  def main(): Unit = super.main(Array())
}
```

## More complex states
It's quite rare that you can get away with just one `SignallingRef`. 
For this example, let's save the number of times counter has been altered
in a separate SignallingRef:

```scala
class Store[F[_]](
  val counter: SignallingRef[F, Int],
  val changes: SignallingRef[F, Int],
)

object Store {
  def make[F[_]: Concurrent] =
    (SignallingRef[F, Int](0), SignallingRef[F, Int](0)).mapN(new Store[F](_, _))
}
```

### Store DSL
Given how unwieldy these constructors can grow, shironeko has a DSL that
you can use to create the store more declaratively:

```scala
class Store[F[_]](dsl: StoreDSL[F]) {
  import dsl._
  val counter = cell(0)
  val changes = cell(0)
}

object Store {
  def make[F[_]: Concurrent] =
    StoreDSL[F].use(new Store[F](_).pure[F])
}
```

`StoreDSL` is a Resource that cannot be used after the constructor has
been executed. Its methods bypass referential transparency to create 
signalling refs immediately. Because of this, you _must_ use `val`, not
`lazy val` or `def` and also you cannot store the `dsl` somewhere for
other state allocation (it'll crash).

Also, if you use DSL methods in `object`s defined inside your store,
beware that objects are initialized lazily, when first demanded. 

## Writing actions

You don't have to put every state update inline into the rendered
component. When logic grows reasonably complex, you can write them
anywhere - just remember that store and `Concurrent` instance are given
for you in the implicit scope in the body of `render`. For example, you
can write:

```scala
object CounterActions {
  def increment[F[_]: Monad](implicit S: Store[F]): F[Unit] =
    S.counter.modify(_ + 1) >> S.changes.modify(_ + 1)
    

  def decrement[F[_]: Monad](implicit S: Store[F]): F[Unit] =
    S.counter.modify(_ - 1) >> S.changes.modify(_ + 1)
}
```

And, since it's just plain effect datatypes, there's zero reason why
we can't just factor out repeating parts:

```scala
object CounterActions {
  private[this] def change[F[_]: Monad](by: Int)(implicit S: Store): F[Unit] =
    S.counter.modify(_ + by) >> S.changes.modify(_ + 1)
    
  def increment[F[_]: Monad](implicit S: Store[F]): F[Unit] =
    change[F](1)

  def decrement[F[_]: Monad](implicit S: Store[F]): F[Unit] =
    change[F](-1)
}
```

You may delete increment/decrement and use `change` directly. Your call.

## Combining multiple cells
Let's revisit our display component. We need to show multiple values
at the same time. We can't `flatMap` two calls to `discrete` - that
gives us an endless stream of pairs of all values ever came through our
app. What is needed is parallel combination - take the latest value that
has arrived in each `SignallingRef`, and emit these pairs of latest
values.

Shironeko provides a blackbox macro `util.combine` that allows you to
construct a stream of case class instances out of several streams, one
per each field. It also works for tuples, if you don't like nicely named
fields.

The construct is `combine[A].from(stream1, stream2, ...)`. A concrete 
type `A` needs to always be specified, as it guides macro inference.

```scala
object CounterDisplay extends Connector.ContainerNoProps {
  case class State(value: Int, changed: Int)
  
  override def subscribe[F[_]: Subscribe]: Stream[F, State] = {
    val S = getAlgebra
    combine[State].from(
      S.counter.discrete,
      S.changes.discrete
    )
  }

  override def render[F[_]: Render](state: State) = {
    div(
      button(onClick := toCallback { CounterActions.decrement[F] })("-"),
      s"Current value is ${state.value}, changed ${state.changed} times",
      button(onClick := toCallback { CounterActions.increment[F] })("+"),
    )
  }
}
```

## Using event-based model

TODO

