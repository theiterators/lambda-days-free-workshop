package pl.iterators.forum.resources

import akka.http.scaladsl.server.Route
import pl.iterators.forum.domain.MyAccount
import play.api.libs.json.{Json, OWrites}

object MyAccountResource {
  trait MyAccountProtocol extends AccountsResource.AccountsProtocol {
    implicit val mayAccountWrites: OWrites[MyAccount] = Json.writes[MyAccount]
  }
}

trait MyAccountResource extends Resource with MyAccountResource.MyAccountProtocol {
  val myAccountRoute: Route = pathEndOrSingleSlash {
    extractClaims { claims =>
      completeAsResource(claims.toMyAccountWithId)(accountPath)
    }
  }
}
