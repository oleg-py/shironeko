---
layout: docs
title: Basic concepts
position: 2
---

### Actions
Actions represent what needs to be done in response to something
happening in your application.

Unlike libraries like Redux, where "actions" are plain data to be
interpreted by some other entity, Shironeko uses cats-effect compatible
type that can execute literally anything. This is a lot of freedom, and
you lose some perks of plain data model, but there are benefits:

#### Pure FP


#### Composition & asynchrony are baked in
Async computations, such as AJAX requests, are bread and butter of
frontend applications. With Shironeko, you can combine multiple requests
in one go with familiar operators:

```scala
val doThingA: F[Unit] = ???
val doThingB: F[Unit] = ???

val doBothThingsSequentially = doThingA >> doThingB
val doBothThingsInParallel   = doThingA.start >> doThingB
```

No middleware is required, no extra types to learn and you can use
any cats-effect compatible library (e.g. sttp or Hammock for AJAX) out
of the box.


#### No boilerplate, simple model
- You write things you want to run
- You run those things by triggers in your UI/timers/etc.

There are no special classes to extend, no complicated machinery of what
needs to do what. Code reuse is achieved with regular techniques, such
as extracting a method, and you can always return an intermediate value
as `F[A]` if you need to use results somehow.

### Streams
Shironeko-specific components can update themselves based on data
coming in any `fs2.Stream`.

If you are not familiar with fs2, it's similar to evaluating some
effectful potentially async computation (driven by `F`, stream's first
type parameter) and giving you all intermediate results as soon as you
can process them (of type `A`, second type parameter). Async 
computations doesn't actually mean that you'd be sending requests left
and right, it can be as simple as waiting for some value to change.

While not needed for basic use of Shironeko, streams come with many
combinators, e.g.
- You can strip and/or transform data coming to you with `map` and
`evalMap`
- You can optimize away unwanted updates with `.filter` or `.distinct`
- You can reduce the frequency of updates with `.debounce`*
- You can use `Stream.resource(..)` to have a mount/unmount lifecycle
hook, and `evalTap` to get an update hook.
- You can create a simple timed update with `Stream.awakeEvery`.*

\* - you need your algebra to provide a Timer, or use type like Monix
Task which always has it

### State cells
State cells is where your application state is stored, and can be
changed or observed by React. Shironeko uses `SignallingRef[F, A]` from
`fs2`, which gives you mutation API of `Ref` and ability to observe
writes as `fs2.Stream` by calling it's `.discrete` method.


### Store
Store is an object, or a tagless algebra in tagless style, which
contains data you want to be manipuating (displaying and modifying).

There's no base class or anything like that, however, tagless style
requires a single "hole" for the type constructor in the type, i.e.

```
class Store[F[_]] // <- like this, a.k.a. kind (* -> *) -> *
```

#### DSL
To simplify definition of multiple state cells, Shironeko provides a
small DSL.

```scala
class Store(dsl: StoreDSL[IO]) {
  import dsl._
  
  // Constructing state cells
  val counter = cell(0)
  val name    = cell("")
  
  // event streams
  val updates = events[String]
  
  // and also plain Refs, for things you don't want to show
  val hiddenState = ref(List[String]())
}

object Store {
  // `construct` is a factory method
  // TODO CHECK INFERENCE HERE
  def make: IO[Store] = StoreDSL.construct(new Store(_))
}
```

DSL is deliberately not referentially transparent - conventional
alternative is more boilerplatey:

```scala
class Store (
  val counter: Cell[IO, Int],
  val name:    Cell[IO, String],
  val updates: Events[IO, String],
  val hiddenState: Ref[IO, List[String]]
)

object Store {
  def make: IO[Store] =
    (Cell[IO, Int](0), Cell[IO, String](""), Events[IO, String], Ref[IO].of(List[String]()))
      .mapN(new Store(_, _, _, _))
}
```

It makes it harder to do modifications such as introducing / removing new
cells, and with types more complex than Int/String, duplication becomes
quite significant.

DSL is also protected in a way that you only can use it once, while
the object is being constructed. E.g. doing something like this
```
def mkExtraCell = cell(50)
```
will fail at runtime. Same for `lazy val` and fields in `object`s (these
need to be triggered manually)

However, DSL is optional. If it irks you, don't use it.

### Connector

Connector is a special object that links your store to React.
It provides a number of internal classes for you to extend in order
to create a _container_ component (see section below).

Recommended usage is to just have a global object:
```scala
object Connector extends DirectConnector[IO, Store]
```

or, in tagless style:

```scala
object Connector extends TaglessConnector[StoreF]
```

There's one method you might want to override:
```scala
def reportUncaughtException(e: Throwable): Unit
```

Actually _using_ a connector requires `ConcurrentEffect` instance for
your effect type. The easiest option is to wire everything up in main
of `IOApp` or similar type, where this instance should be available.

A store needs to be supplied to use a connector, either explicitly:

```scala
ReactDOM.render(Connector(store)(Routes()), domElement)
```

or as an implicit:

```scala
implicit val s: Store = store
ReactDOM.render(Connector(Routes()), domElement)
```

Connector needs to be higher in rendering tree, otherwise an exception
will be raised. You only need to use it once per store, however.

```scala
// Valid, recommended
Connector(Routes())
// Valid, recommended
Connector(div(Routes(), Routes()))

// Valid, but not recommended
div(
  Connector(Routes()),
  Connector(Routes())
)
// Broken
div(
  Connector(Routes()),
  Routes()
)
```

### Container

```scala mdoc
val x = 1
```