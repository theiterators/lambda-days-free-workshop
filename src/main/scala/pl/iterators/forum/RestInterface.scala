package pl.iterators.forum

import akka.http.scaladsl.server.Route
import pl.iterators.forum.resources.{AccountsResource, MyAccountResource}

abstract class RestInterface extends Resources {
  def routes: Route = path("my-account") { myAccountRoute } ~ path("accounts") { accountsRoutes }
}

trait Resources extends MyAccountResource with AccountsResource
