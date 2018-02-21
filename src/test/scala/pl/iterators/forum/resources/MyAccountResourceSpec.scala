package pl.iterators.forum.resources

import akka.http.scaladsl.model.StatusCodes.{OK, Unauthorized}
import akka.http.scaladsl.server.Route
import pl.iterators.forum.domain.{AccountId, Claims, Email, Nick}
import play.api.libs.json._

class MyAccountResourceSpec extends BaseSpec {
  private val testRoute = Route.seal(restInterface.myAccountRoute)

  feature("My account") {
    scenario("Valid request") {
      val claims = Claims(AccountId(100), Email("admin@forum.com"), Nick("Admin"), isAdmin = true)
      withJwtToken(Get("/"), claims) ~> testRoute ~> check {
        status shouldEqual OK

        val claims = responseAs[JsObject]
        claims.value.toList should contain only ("email" -> JsString("admin@forum.com"),
        "isAdmin"                                        -> JsBoolean(true),
        "nick"                                           -> JsString("Admin"),
        "id"                                             -> JsNumber(100),
        "uri"                                            -> JsString("http://example.com/api/accounts/100"))
      }
    }
    scenario("Unauthorized request") {
      Get("/") ~> testRoute ~> check {
        status shouldEqual Unauthorized
      }
    }
  }
}
