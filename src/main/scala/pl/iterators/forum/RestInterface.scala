package pl.iterators.forum

import akka.http.scaladsl.server.Route
import pl.iterators.forum.resources._

abstract class RestInterface extends Resources {
  def routes: Route = path("auth") { authRoutes } ~ path("my-account") { myAccountRoute } ~ path("accounts") { accountsRoutes }
}

trait Resources extends MyAccountResource with AccountsResource with AuthResource
