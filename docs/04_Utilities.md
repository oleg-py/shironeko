---
layout: docs
title: Misc. utilities
position: 4
---

## `combine` - parallel merging of fs2.Streams
`combine` takes a name of class, which companion has apply
method (e.g. tuple or case class) and builds a stream of values
of this class, built from the most recent values of each stream.

This is done by converting all streams to `Signal` using `holdOption`,
merging them using `Applicative[Signal]` which does parallel joining,
removing all `None` values and mapping the resulting output. The method
itself is a varargs blackbox macro, allowing for any arity of
constructor function, and is based on `withLatestFrom` in various Rx
libraries.

The call looks like this:
```scala
case class State(timestamp: String, count: Int, name: String)

val states = combine[State].from(
    // ^ --- ^ type parameter is mandatory there
  stream1, // <- streams will be checked to match expected param type
  stream2, //    after macro expansion
  stream3
)
```

## `shift` - using hooks in any React component
React hooks cannot be used with regular class-based components, and
separating parts of state or using regular context API can be annoying.
Shironeko Containers also have no place to specify an extra local state,
which might be desirable in several scenarios.

Shift is a simple functional component that takes a (by-name) body
and renders it directly. This body, however, might use any hooks. This
can be used in containers, or in plain class-based components, e.g.
in this class from todo-mvc example:
```scala
@react class NewTodoInput extends StatelessComponent {
  case class Props(onCommit: String => Unit)

  override def render(): ReactElement = shift {
    val (text, setText) = Hooks.useState("")
    input(
      className := "new-todo",
      placeholder := "What needs to be done?",
      autoFocus := true,
      value := text,
      onChange := { e => setText(e.target.value) },
      onKeyPress := { e =>
        if (e.key == "Enter") {
          props.onCommit(text)
          setText("")
        }
      }
    )
  }
}
```

## `Cache` - low-boilerplate cache for reducing allocations

`Cache` is a macro-based polymorphic cache. It uses
call position in the code as a primary key.

<!--TODO check inference-->
```scala
class Foo extends StatelessComponent { 
  type Props = Unit
  private val cache = new Cache
  
  def render: ReactElement = {
    div(
      onMouseEnter := cache { () => println("Hovered") },
      onClick := cache { () => println("Clicked") }
    )(cache("Click me!")) // not useful here
  }
}
```

Its primary purpose is to avoid allocating lambdas in `render`
method, but it can be used for anything else (it might be confusing
though).

It also has support for adding a key and a list of dependencies to
watch. You must use key in loops, and add any values your lambda
captures that are subject to change to a dependency list

```scala
class Foo extends StatelessComponent { 
  type Props = Int
  private val cache = new Cache
  
  def render: ReactElement = {
    val displayValue = props + 1
    div(
      List("foo", "bar", "baz").map { s =>
        // cache different funcs for the same position
        div(onClick := cache(s)(() => println(s)))
      },
      div(onClick := cache.dependent("clicker", Seq(displayValue)) { () =>
        println(s"Current value is $displayValue")
      })(displayValue)
    )
  }
}
```
Note that you don't need to add `state` or `props` as dependencies in
Slinky, as those are methods and will always resolve to most recent state.
