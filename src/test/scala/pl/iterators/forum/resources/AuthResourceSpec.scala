package pl.iterators.forum.resources

import java.time.Instant

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import org.scalatest.OptionValues
import pl.iterators.forum.domain._
import pl.iterators.forum.domain.tags.PasswordTag
import pl.iterators.forum.fixtures.{AccountFixture, RefreshTokenFixture}
import pl.iterators.forum.resources.AuthResource.AuthProtocol
import pl.iterators.forum.services.AuthenticationService.{AuthRequest, RefreshTokenRequest}
import pl.iterators.forum.utils.tag._
import play.api.libs.json._

class AuthResourceSpec extends BaseSpec with AuthProtocol with OptionValues {

  private val accounts       = new AccountFixture with RefreshTokenFixture
  private lazy val testRoute = Route.seal(restInterface.authRoutes)

  override val accountRepository      = accounts.accountInterpreter andThen inFuture
  override val refreshTokenRepository = accounts.tokenInterpreter andThen inFuture

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

  feature("Refresh tokens") {
    val account = accounts.existingAccount
    scenario("Obtain token") {
      withJwtToken(Post("/refresh-token"), account.claims) ~> testRoute ~> check {
        status shouldEqual OK

        val refreshToken = responseAs[JsObject]
        refreshToken.value.keySet should contain only ("token", "expiresAt")

        val savedRefreshToken = accounts.tokenInterpreter.query(account.email, token = refreshToken("token").as[String]).value
        refreshToken("expiresAt").as[Instant] shouldEqual savedRefreshToken.expiresAtInstant(authenticationService.refreshTokenTtl)
      }
    }
    scenario("Obtain token without authorization") {
      Post("/refresh-token") ~> testRoute ~> check {
        status shouldEqual Unauthorized
      }
    }
    scenario("Obtain token when banned") {
      val bannedAccount = accounts.evilUser
      withJwtToken(Post("/refresh-token"), bannedAccount.claims) ~> testRoute ~> check {
        status shouldEqual NotFound
      }
    }
    scenario("Obtain JWT token with refresh token") {
      val refreshToken = accounts.createToken(account.email)
      Post("/token", RefreshTokenRequest(account.email, refreshToken)) ~> testRoute ~> check {
        status shouldEqual OK

        val token  = responseAs[JsObject]
        val claims = jwtValidateAndDecode(token)

        claims.isAdmin shouldBe false
        claims.email shouldEqual account.email
        claims.nick shouldEqual account.confirmedNick
      }
    }
    scenario("Obtain JWT token with expired refresh token") {
      val expiredToken = accounts.createExpiredToken(account.email, refreshTokenTtl)
      Post("/token", RefreshTokenRequest(account.email, expiredToken)) ~> testRoute ~> check {
        status shouldEqual BadRequest

        val error = responseAs[JsObject]
        error shouldEqual JsObject(Map("error" -> JsString("TokenExpired")))
      }
    }
  }

}
