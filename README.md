# Shironeko
*A cat that can manage state*

Shironeko is a state management library for Scala.js with the following goals:

- Make simple things trivial and hard things possible without boilerplate
- Support writing logic in pure FP, with cats-effect and final tagless style
- Be developer and IDE-friendly

## Quick start

```scala
libraryDependencies += "com.olegpy" %%% "shironeko" % "0.0.1"
```

### Your first store
First, you need to get a grip on a `ConcurrentEffect` instance for a desired effect type. With cats' IO, it can be done using `IOApp`:

```scala
object Main extends IOApp {
  val Instance = ConcurrentEffect[IO]
}
```

The recommended way is to have a global store. You need to pass this instance into constructor of a `StoreBase`. Then, you define your state cells inside like this:

```scala

object Store extends StoreBase[IO](Main.Instance) with ImpureIntegration[IO] {
  val counter = Cell(0)
}

```

Then, define some actions. Actions are just `F[Unit]` values of desired effect type. They can be placed anywhere, but you will need to access them at the place where you want them to be executed (e.g. in React component).

```scala
object Actions {
  val increment: IO[Unit] = Store.counter.update(_ + 1)
  val decrement: IO[Unit] = Store.counter.update(_ - 1)
}
```

## Core abstractions
### State cells (`Cell`)
State cells are `Ref`s (mutable objects that can be modified in `F`) with added `listen` method which returns `fs2.Stream[F, A]`, allowing you to watch for every update of a cell - a useful ability to implement component re-rendering. 

```scala
trait Cell[F[_], A] extends Ref[F, A] {
  def listen: Stream[F, A]
}
```

### Event streams (`Events`)
Event streams are essentially `fs2.Topic[F, A]`. Currently API supports emitting a single event or a stream, awaiting for a single specific event, or listening to event stream altogether.

Unlike regular `Topic`s, the latest element is not exposed when you `listen` to an event stream. Only values arriving after subscription time will be received.

```scala
trait Events[F[_], A] {
  def emit: Sink[F, A]
  def emit1(a: A): F[Unit]
  def listen: Stream[F, A]
  def await1[B](pf: PartialFunction[A, B]): F[B]
}
```

### Actions (`F[Unit]`)
Actions are just effectful values you `exec` at the edge of the world (e.g. in you React facade event handlers) to perform your logic. There's no library or custom type required to define them.