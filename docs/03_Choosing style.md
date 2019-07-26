---
layout: docs
title: Choosing style
position: 3
---
Shironeko is a very unopinionated library, providing you
with multiple options for building your application. In particular,
you can use tagless style, where state container is parameterized with
an effect type, or direct style with a concrete effect type. You can
also use event-sourcing and hide direct state manipulation, or expose
state cells directly in algebra. You can put actions into state container
itself, or write them outside in a separate `class`/`object`, make
some parts private and expose only minimum needed to render the app. You
can also use multiple algebras for different state parts.

If you are not sure where to even begin, I'll make the choices for you:
- If you're using cats-effect IO, go with tagless style. If you're using
  monix or ZIO, go with direct style.
- Start with one algebra and one connector.
- Start with direct state manipulation and exposed state cells.
  Avoid event sourcing as much as possible. Don't encapsulate anything,
  but put related state pieces into a single cell as a case class.
- Put all actions into separate objects/classes. Don't write them inline
  and don't put them together with app state.


## Tagless or non-tagless

Consider tagless style if you're familiar with technique,
want to exercise more discipline or use `cats.effect.IO`,
there is little benefit to this concrete type.

Use direct style if you're using capabilities of a more powerful effect
type. For example, monix `Task` has `Parallel`, `Timer` and
`ContextShift` always available in scope, allowing you to `.debounce`
any `Stream` and use `parMapN`-like operators directly.

The connector definition is not different, and you still get a Concurrent
instance and an implicit of your choice propagated - however, in
direct style, the implicit must not be a type constructor.

There are also no `ContainerF` base classes, as they are not necessary.

Finally, there is no restriction on `exec`/`toCallback` method usage.
And, since you know which effect you're using, you might use
its methods (like `unsafeRunAsyncAndForget` on IO) directly if the type
allows it.

Other than that, the API/usage is very similar in two styles:

```scala mdoc
// Algebras
class AlgebraF[F[_]](dsl: StoreDSL[F]) {
  import dsl._
  val state = cell(42)
  val log   = cell(List.empty[String])
}

class Algebra(dsl: StoreDSL[Task]) {
  import dsl._
  val state = cell(42)
  val log   = cell(List.empty[String])
}

// Connectors
object Connector1 extends TaglessConnector[AlgebraF]
object Connector2 extends DirectConnector[Task, Algebra]

// Containers

object TestContainer extends Connector1.ContainerNoProps {
  case class State(i: Int, l: List[String])
  def subscribe[F[_]: Subscribe]: fs2.Stream[F, State] = {
    val F = getAlgebra
    combine[State].from(
      F.state.discrete,
      F.log.discrete
    )
  }
  
  def render[F[_]: Render](s: State) = 
    div(
      div(state.i, onClick := toCallback { getAlgebra.log.update("Clicked" :: _) }),
      div(state.l.mkString(", "))
    )
}

object TestContainer extends Connector2.ContainerNoProps {
  case class State(i: Int, l: List[String])
  def subscribe: fs2.Stream[Task, State] = {
    val F = getAlgebra
    combine[State].from(
      F.state.discrete,
      F.log.discrete
    )
  }
  
  def render(s: State) = 
    div(
      div(state.i, onClick := toCallback { getAlgebra.log.update("Clicked" :: _) }),
      div(state.l.mkString(", "))
    )
}
```

## Event sourcing vs direct modification

I recommend not using event sourcing as a method for single state
changes. You can always change multiple values in one action:

```scala
def updateCounter[F[_]: Monad](implicit F: Algebra[F]) =
  F.counter.update(_ + 1) >> F.updated.set(true)
```

The power of event streams comes from its async control flow features,
allowing for fairly complex, but very _manual_ interaction between
multiple semantically independent actions.

Consider this simple example:
```scala
// Regular action
val renderer: F[Unit] = for {
  _    <- F.message.set(s"Starting a data request")
  id   <- F.ids.modify(x => (x + 1, x + 1))
  _    <- requestor(id).start
  _    <- F.events.await1 { case Accepted(`id`) => () }
  _    <- F.message.set(s"Request $id was accepted")
  data <- F.events.await1 { case Completed(`id`, data) => data }
  _    <- F.message.set(s"Request $id completed, god $data")
} yield ()

def requestor(id: Int): F[Unit] = for {
  _    <- backend.put(s"/request/$id")
  _    <- F.events.emit1(Accepted(`id`))
  data <- backend.get(s"/request/$id/result")
  _    <- F.events.emit1(Completed(id, data))
} yield () 
  

// Set this one up in the `main`
val logger = F.events.onNextDo {
  case Completed(id, data) => F.log.update(s"Request $id: $data" +: _)
  case _ => ().pure[F]
}
```
While powerful and convenient, it lacks proper error handling, and an error
in `requestor` will cause the continuation of `renderer` to hang forever.
Using return values (F[A]) where possible could solve that:

```scala
val renderer: F[Unit] = for {
  _    <- F.message.set(s"Starting a data request")
  id   <- F.ids.modify(x => (x + 1, x + 1))
  data <- requestor(id)
  _    <- F.message.set(s"Request $id completed, god $data")
} yield ()

def requestor(id: Int): F[Data] = for {
  _    <- backend.put(s"/request/$id")
  _    <- F.message.set(s"Request $id was accepted")
  data <- backend.get(s"/request/$id/result")
  _    <- F.events.emit1 { Completed(id, data) }
} yield data
  

// This one is okay, it's not waiting forever.
val logger = F.events.onNextDo {
  case Completed(id, data) => F.log.update(s"Request $id: $data" +: _)
  case _ => ().pure[F]
}
```

