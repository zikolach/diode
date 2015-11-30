# Usage with React

Although Diode is totally framework agnostic, it does work very well together with [React](https://facebook.github.io/react/). To simplify integration of Diode
with your [scalajs-react](https://github.com/japgolly/scalajs-react) application, use a separate library `diode-react`.

To use Diode React in your application add following dependency declaration to your Scala.js project.

<pre><code class="lang-scala">"me.chrons" %%% "diode-react" % "{{ book.version }}"</code></pre>

## Overview

In React the user interface is build out of a hierarchy of _components_, each receiving _props_ and optionally having internal _state_. With Diode you'll want
most of your React components to be _stateless_ because state is managed by the Diode circuit. Sometimes, however, it is useful to maintain some state in the
UI component, for example when editing an item.

A very simple integration can be achieved just by passing a value from a _model reader_ to your React component in its props. This works well for dummy _leaf_
components that just display data. This approach, however, has a few downsides:

1. Your component is not notified of any model changes
2. Your component cannot dispatch any actions

## Wrap and Connect Components

To simplify connecting your Diode circuit to your React components, Diode React provides a way to _wrap_ and _connect_ components. First step is to add the
`ReactConnector` trait to your application `Circuit` class.

```scala
case class RootModel(data: Seq[String], asyncData: Pot[String])

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] { ... }
```

Now your `AppCircuit` is equipped with two new methods: `wrap` and `connect`. Both methods have almost the same signature, taking a zoom function (or
alternatively a model reader) and a function to build a React component.

```scala
def wrap[S, C](zoomFunc: M => S)(compB: ModelProxy[S] => C)
def connect[S, C](zoomFunc: M => S)(compB: ModelProxy[S] => C)
```

The difference between these two is that `wrap` just creates a `ModelProxy` and passes it to your component builder function, while `connect` actually creates
another React component that surrounds your own component.

> Use `wrap` when your component doesn't need model updates from Diode, and `connect` when it does. Even if you use `wrap` on a top component, you can still
`connect` components underneath it.

When wrapping or connecting a component, you can either pass the `ModelProxy` directly as props, or use it in the builder function to pass relevant props to
your component.

```scala
// connect with ModelProxy
val smartComponent = ReactComponentB[ModelProxy[Seq[String]]("SmartComponent").build
...
val sc = AppCircuit.connect(_.data)(p => smartComponent(p))

// wrap with specific props
case class Props(data: Seq[String], onClick: Callback)
val dummyComponent = ReactComponentB[Props]("DummyComponent").build
...
val dc = AppCircuit.wrap(_.data)(p => dummyComponent(Props(p(), p.dispatch(DummyClicked)))

def render = <.div(sc, dc)
```

The `ModelProxy` provides a `dispatch` function that wraps the dispatch call in a React `Callback`, making it easy to integrate with event
handlers etc. It also provides `wrap` and `connect`, allowing your component to connect sub-components to the Diode circuit.

```scala
val Dashboard = ReactComponentB[ModelProxy[RootModel]("Dashboard")
  .render_P { proxy =>
    <.div(
      h3("Data"),
      proxy.connect(_.asyncData)(p => AsyncDataView(p), // pass ModelProxy
      proxy.wrap(_.data)(p => DataView(p()), // just pass the value
      <.button(^.onClick --> proxy.dispatch(RefreshData), "Refresh") 
    )
  }
  .build
```  

## Rendering `Pot`

Because a `Pot` can exist in many states, it's desirable to reflect these states in your view components. Diode React extends `Pot` by adding convenient
rendering methods through `ReactPot`.

```scala
// use import to get access to implicit extension methods
import diode.react.ReactPot._
```

An example from the [SPA tutorial](https://github.com/ochrons/scalajs-spa-tutorial)

```scala
val Motd = ReactComponentB[ModelProxy[Pot[String]]]("Motd")
  .render_P { proxy =>
    Panel(Panel.Props("Message of the day"),
      // render messages depending on the state of the Pot
      proxy().renderPending(_ > 500, _ => <.p("Loading...")),
      proxy().renderFailed(ex => <.p("Failed to load")),
      proxy().render(m => <.p(m)),
      Button(Button.Props(proxy.dispatch(UpdateMotd()), CommonStyle.danger), Icon.refresh, " Update")
    )
  }
```

Each of the rendering functions optionally renders the provided content, depending on the state of `Pot`. In `renderPending` you can supply a condition based
on the duration of the pending request, so that the UI will show a "Loading" message only after some time has elapsed. This of course requires that your action
is updating the model at suitable intervals for the model to update.