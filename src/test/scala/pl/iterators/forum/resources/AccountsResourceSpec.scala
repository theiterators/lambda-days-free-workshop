package pl.iterators.forum.resources

import akka.http.scaladsl.model.ContentTypes.`text/plain(UTF-8)`
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import org.scalatest.{LoneElement, OptionValues}
import pl.iterators.forum.domain._
import pl.iterators.forum.fixtures.{AccountFixture, ConfirmationTokenFixture}
import pl.iterators.forum.resources.AccountsResource.AccountsProtocol
import pl.iterators.forum.services.AccountService.AccountCreateRequest
import play.api.libs.json._

class AccountsResourceSpec extends BaseSpec with AccountsProtocol with OptionValues with LoneElement {
  private val accounts       = new AccountFixture with ConfirmationTokenFixture
  private lazy val testRoute = Route.seal(restInterface.accountsRoutes)

  override val accountRepository           = accounts.accountInterpreter andThen inFuture
  override val confirmationTokenRepository = accounts.tokenInterpreter andThen inFuture

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

  feature("Account creation") {

    scenario("Valid request") {
      Post("/", AccountCreateRequest(Email("user1@forum.com"), PasswordPlain("Iteratorz!1"))) ~> testRoute ~> check {
        status shouldEqual Created

        val response = responseAs[JsObject]
        val id       = response.value("id").as[Int]
        response.value.toList should contain allOf ("email" -> JsString("user1@forum.com"),
        "isAdmin"                                           -> JsBoolean(false),
        "createdAt"                                         -> Writes.DefaultOffsetDateTimeWrites.writes(accounts.nowValue),
        "banned"                                            -> JsBoolean(false),
        "confirmed"                                         -> JsBoolean(false),
        "uri"                                               -> JsString(s"http://example.com/api/accounts/$id"))
        header[Location] shouldEqual Some(Location(s"http://example.com/api/accounts/$id"))
      }
    }

    scenario("Duplicate email") {
      Post("/", AccountCreateRequest(accounts.adminAccount.email, PasswordPlain("Iteratorz!1"))) ~> testRoute ~> check {
        status shouldEqual Conflict
        val error = responseAs[JsObject]
        error shouldEqual JsObject(Map("error" -> JsString("EmailNotUnique")))
      }
    }

    scenario("Password too weak") {
      Post("/", AccountCreateRequest(Email("user3@forum.com"), PasswordPlain("badpass"))) ~> testRoute ~> check {
        status shouldEqual Conflict
        val error = responseAs[JsObject]
        error shouldEqual JsObject(Map("error" -> JsString("PasswordTooWeak")))
      }
    }
  }

  feature("Account update") {
    val account      = accounts.adminAccount
    val otherAccount = accounts.existingAccount

    scenario("Valid request") {
      withJwtToken(Patch(s"/${account.id}", JsObject(Seq("about" -> JsString("best admin in the world")))), account.claims) ~> testRoute ~> check {
        status shouldEqual OK

        val response = responseAs[JsObject]
        response.value.toList should contain allOf ("email" -> JsString(account.email),
        "nick"                                              -> JsString(account.confirmedNick),
        "isAdmin"                                           -> JsBoolean(account.isAdmin),
        "createdAt"                                         -> Writes.DefaultOffsetDateTimeWrites.writes(accounts.nowValue),
        "banned"                                            -> JsBoolean(account.isBanned),
        "confirmed"                                         -> JsBoolean(account.isConfirmed),
        "uri"                                               -> JsString(s"http://example.com/api/accounts/${account.id}"),
        "id"                                                -> JsNumber(account.id),
        "about"                                             -> JsString("best admin in the world"))
      }
    }

    scenario("Update other account") {
      withJwtToken(Patch(s"/${otherAccount.id}", JsObject(Seq("about" -> JsNull))), account.claims) ~> testRoute ~> check {
        status shouldEqual Forbidden
      }
    }
  }

  feature("Query account by nick") {
    val adminAccount = accounts.adminAccount

    scenario("Existing account") {
      val queriedAccount = accounts.existingAccount
      withJwtToken(Get("/?nick=John%20Doe"), adminAccount.claims) ~> testRoute ~> check {
        status shouldEqual OK

        val response = responseAs[JsObject]
        response.value.keySet should contain only ("email", "nick", "isAdmin", "uri", "banned", "confirmed", "id", "createdAt")
        response.value.toList should contain allOf ("email" -> JsString(queriedAccount.email),
        "nick"                                              -> JsString("John Doe"),
        "isAdmin"                                           -> JsBoolean(queriedAccount.isAdmin),
        "uri"                                               -> JsString(s"http://example.com/api/accounts/${queriedAccount.id}"))
      }
    }

    scenario("Not existing account") {
      withJwtToken(Get("/?nick=No-one"), adminAccount.claims) ~> testRoute ~> check {
        status shouldEqual NotFound
      }
    }

    scenario("Not admin") {
      val notAdminAccount = accounts.existingAccount
      withJwtToken(Get("/?nick=Master%20of%20the%20Universe"), notAdminAccount.claims) ~> testRoute ~> check {
        status shouldEqual Forbidden
      }
    }

    scenario("Nick shorter than 3 characters") {
      withJwtToken(Get("/?nick=a"), adminAccount.claims) ~> testRoute ~> check {
        status shouldEqual BadRequest
        contentType shouldEqual `text/plain(UTF-8)`
      }
    }

    scenario("Nick longer than 25 characters") {
      withJwtToken(Get(s"/?nick=${"a" * 26}"), adminAccount.claims) ~> testRoute ~> check {
        status shouldEqual BadRequest
        contentType shouldEqual `text/plain(UTF-8)`
      }
    }
  }

  feature("Checking if nick exists") {

    scenario("Exists") {
      Head("/?nick=John%20Doe") ~> testRoute ~> check {
        status shouldEqual NoContent
      }
    }

    scenario("Not exists") {
      Head("/?nick=No-one") ~> testRoute ~> check {
        status shouldEqual NotFound
      }
    }

  }

}
