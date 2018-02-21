package pl.iterators.forum.resources

import akka.http.scaladsl.model.ContentTypes.`text/plain(UTF-8)`
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import org.scalatest.{LoneElement, OptionValues}
import pl.iterators.forum.domain._
import pl.iterators.forum.fixtures.AccountFixture
import pl.iterators.forum.resources.AccountsResource.AccountsProtocol
import play.api.libs.json._

class AccountsResourceSpec extends BaseSpec with AccountsProtocol with OptionValues with LoneElement {
  private val accounts       = new AccountFixture
  private lazy val testRoute = Route.seal(restInterface.accountsRoutes)

  override val accountRepository = accounts.accountInterpreter andThen inFuture

  feature("Account lookup") {
    val account = accounts.existingAccount

    val accountFields = Seq(
      "email"     -> JsString("user@forum.com"),
      "nick"      -> JsString("John Doe"),
      "isAdmin"   -> JsBoolean(false),
      "banned"    -> JsBoolean(false),
      "confirmed" -> JsBoolean(true),
      "id"        -> JsNumber(account.id),
      "uri"       -> JsString(s"http://example.com/api/accounts/${account.id}"),
      "createdAt" -> Writes.DefaultOffsetDateTimeWrites.writes(account.createdAt)
    )

    scenario("Self account") {
      withJwtToken(Get(s"/${account.id}"), account.claims) ~> testRoute ~> check {
        status shouldEqual OK

        val response = responseAs[JsObject]
        response.value.toList should contain allElementsOf accountFields
      }
    }

    scenario("Any account if requested by admin") {
      val adminAccount = accounts.adminAccount
      withJwtToken(Get(s"/${account.id}"), adminAccount.claims) ~> testRoute ~> check {
        status shouldEqual OK

        val response = responseAs[JsObject]
        response.value.toList should contain allElementsOf accountFields
      }
    }

    scenario("Other account") {
      withJwtToken(Get("/100"), account.claims) ~> testRoute ~> check {
        status shouldEqual Forbidden
      }
    }
  }

  feature("Query account by email") {
    val adminAccount = accounts.adminAccount

    scenario("Existing account") {
      val queriedAccount = accounts.existingAccount
      withJwtToken(Get(s"/?email=${queriedAccount.email.uriEncode}"), adminAccount.claims) ~> testRoute ~> check {
        status shouldEqual OK

        val response = responseAs[JsObject]
        response.value.keySet should contain only ("email", "nick", "isAdmin", "uri", "banned", "confirmed", "id", "createdAt")
        response.value.toList should contain allOf ("email" -> JsString(queriedAccount.email),
        "nick"                                              -> JsString(queriedAccount.confirmedNick),
        "isAdmin"                                           -> JsBoolean(false),
        "uri"                                               -> JsString(s"http://example.com/api/accounts/${queriedAccount.id}"))
      }
    }

    scenario("Not existing account") {
      withJwtToken(Get(s"/?email=${Email("user100@forum.com").uriEncode}"), adminAccount.claims) ~> testRoute ~> check {
        status shouldEqual NotFound
      }
    }

    scenario("Not admin") {
      val notAdminAccount = accounts.existingAccount
      withJwtToken(Get(s"/?email=${Email("admin@forum.com").uriEncode}"), notAdminAccount.claims) ~> testRoute ~> check {
        status shouldEqual Forbidden
      }
    }

    scenario("Invalid email") {
      withJwtToken(Get("/?email=CAFEBABE"), adminAccount.claims) ~> testRoute ~> check {
        status shouldEqual BadRequest
        contentType shouldEqual `text/plain(UTF-8)`
      }
    }
  }

}
