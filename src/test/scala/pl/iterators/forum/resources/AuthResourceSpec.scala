package pl.iterators.forum.resources

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import org.scalatest.OptionValues
import pl.iterators.forum.domain._
import pl.iterators.forum.domain.tags.PasswordTag
import pl.iterators.forum.fixtures.AccountFixture
import pl.iterators.forum.resources.AuthResource.AuthProtocol
import pl.iterators.forum.services.AuthenticationService.AuthRequest
import pl.iterators.forum.utils.tag._
import play.api.libs.json._

class AuthResourceSpec extends BaseSpec with AuthProtocol with OptionValues {

  private val accounts       = new AccountFixture
  private lazy val testRoute = Route.seal(restInterface.authRoutes)

  override val accountRepository = accounts.accountInterpreter andThen inFuture

  feature("User authentication") {
    val validUser       = accounts.existingAccount
    val validAdminUser  = accounts.adminAccount
    val bannedUser      = accounts.evilUser
    val unconfirmedUser = accounts.unconfirmedUser

    scenario("Valid user") {
      Post("/", AuthRequest(validUser.email, PasswordPlain(accounts.existingAccountPlainPassword))) ~> testRoute ~> check {
        status shouldEqual OK

        val token = responseAs[JsObject]
        token.value.keySet should contain only ("token", "expiresAt")

        val claims = jwtValidateAndDecode(token)

        claims.isAdmin shouldBe false
        claims.email shouldEqual validUser.email
      }
    }
    scenario("Valid admin user") {
      Post("/", AuthRequest(validAdminUser.email, PasswordPlain(accounts.adminPlainPassword))) ~> testRoute ~> check {
        status shouldEqual OK

        val token = responseAs[JsObject]
        token.value.keySet should contain only ("token", "expiresAt")

        val claims = jwtValidateAndDecode(token)

        claims.isAdmin shouldBe true
        claims.email shouldEqual validAdminUser.email
      }
    }
    scenario("Invalid password") {
      Post("/", AuthRequest(validUser.email, PasswordPlain("pass?"))) ~> testRoute ~> check {
        status shouldEqual Unauthorized

        val error = responseAs[JsObject]
        error shouldEqual JsObject(Map("error" -> JsString("InvalidCredentials")))
      }
    }
    scenario("Banned user") {
      Post("/", AuthRequest(bannedUser.email, PasswordPlain(accounts.evilUserPassword))) ~> testRoute ~> check {
        status shouldEqual Unauthorized

        val error = responseAs[JsObject]
        error shouldEqual JsObject(Map("error" -> JsString("Banned")))
      }
    }
    scenario("Unconfirmed user") {
      Post("/", AuthRequest(unconfirmedUser.email, PasswordPlain(accounts.unconfirmedUserPassword))) ~> testRoute ~> check {
        status shouldEqual Unauthorized

        val error = responseAs[JsObject]
        error shouldEqual JsObject(Map("error" -> JsString("InvalidCredentials")))
      }
    }
    scenario("Empty password") {
      Post("/", AuthRequest(validUser.email, "".@@[PasswordTag])) ~> testRoute ~> check {
        status shouldEqual BadRequest
        contentType shouldEqual `application/json`
      }
    }
  }

}
