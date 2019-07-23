import slinky.core.facade.React
import slinky.web.ReactDOM

object Main extends App {
  try {
    React.createElement("div", null, null)
    ReactDOM.render(null, null)
  } catch {
    case _: Throwable => ()
  }
}