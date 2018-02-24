package pl.iterators.forum.services

import java.time.Duration

import org.scalatest._
import pl.iterators.forum.domain._
import pl.iterators.forum.fixtures.{AccountFixture, RefreshTokenFixture}
import pl.iterators.forum.services.AuthenticationService.{AuthRequest, RefreshTokenRequest}

class AuthenticationServiceSpecs extends FunSpec with Matchers with EitherValues with OptionValues {

  val authenticationService = new AuthenticationService {
    override val refreshTokenTtl = Duration.ofMinutes(1)
  }

  describe("authenticate") {
    it("should return claims given correct email and password") {
      new AccountFixture {
        val claimsOrError =
          authenticationService
            .authenticate(AuthRequest(existingAccount.email, PasswordPlain(existingAccountPlainPassword)))
            .foldMap(accountInterpreter)

        val claims = claimsOrError.right.value
        claims.email shouldEqual "user@forum.com"
      }
    }

    it("should return error given incorrect email") {
      new AccountFixture {
        val claimsOrError =
          authenticationService.authenticate(AuthRequest(Email("nobody@example.com"), PasswordPlain("pass"))).foldMap(accountInterpreter)

        val error = claimsOrError.left.value
        error shouldBe InvalidCredentials
      }
    }

    it("should return error given incorrect password") {
      new AccountFixture {
        val claimsOrError =
          authenticationService.authenticate(AuthRequest(existingAccount.email, PasswordPlain("I forgot :-("))).foldMap(accountInterpreter)

        val error = claimsOrError.left.value
        error shouldBe InvalidCredentials
      }
    }

    it("should return error when account is banned") {
      new AccountFixture {
        val claimsOrError =
          authenticationService.authenticate(AuthRequest(evilUser.email, PasswordPlain(evilUserPassword))).foldMap(accountInterpreter)

        val error = claimsOrError.left.value
        error shouldBe Banned
      }
    }

    it("should return error when account is not confirmed") {
      new AccountFixture {
        val claimsOrError =
          authenticationService
            .authenticate(AuthRequest(unconfirmedUser.email, PasswordPlain(unconfirmedUserPassword)))
            .foldMap(accountInterpreter)

        val error = claimsOrError.left.value
        error shouldBe InvalidCredentials
      }
    }
  }

  describe("obtain refresh token") {
    it("should return a token given correct email") {
      new AccountFixture with RefreshTokenFixture {
        val maybeRefreshTokenAndDuration =
          authenticationService.obtainRefreshToken(existingAccount.email).foldMap(refreshTokenOrAccountInterpreter)

        maybeRefreshTokenAndDuration shouldBe defined
        val (refreshToken, duration) = maybeRefreshTokenAndDuration.value
        refreshToken.email shouldEqual "user@forum.com"
        duration shouldEqual authenticationService.refreshTokenTtl
      }
    }

    it("should not return any token given invalid email") {
      new AccountFixture with RefreshTokenFixture {
        val maybeRefreshTokenAndDuration =
          authenticationService.obtainRefreshToken(Email("nobody@example.com")).foldMap(refreshTokenOrAccountInterpreter)

        maybeRefreshTokenAndDuration shouldBe empty
      }
    }

    it("should not return any token given banned account") {
      new AccountFixture with RefreshTokenFixture {
        val maybeRefreshTokenAndDuration =
          authenticationService.obtainRefreshToken(evilUser.email).foldMap(refreshTokenOrAccountInterpreter)

        maybeRefreshTokenAndDuration shouldBe empty
      }
    }

    it("should not return any token given unconfirmed account") {
      new AccountFixture with RefreshTokenFixture {
        val maybeRefreshTokenAndDuration =
          authenticationService.obtainRefreshToken(unconfirmedUser.email).foldMap(refreshTokenOrAccountInterpreter)

        maybeRefreshTokenAndDuration shouldBe empty
      }
    }
  }

  describe("refresh claims") {
    it("should return claims given correct refresh token") {
      new AccountFixture with RefreshTokenFixture {
        val token = createToken(existingAccount.email)

        val claimsOrError = authenticationService
          .refreshClaims(RefreshTokenRequest(existingAccount.email, token))
          .foldMap(refreshTokenOrAccountInterpreter)

        val claims = claimsOrError.right.value
        claims.email shouldEqual "user@forum.com"
      }
    }

    it("should return error given incorrect email") {
      new AccountFixture with RefreshTokenFixture {
        val token = createToken(existingAccount.email)

        val claimsOrError =
          authenticationService
            .refreshClaims(RefreshTokenRequest(Email("user100@forum.com"), token))
            .foldMap(refreshTokenOrAccountInterpreter)

        val error = claimsOrError.left.value
        error shouldBe InvalidToken
      }
    }

    it("should return error given incorrect token") {
      new AccountFixture with RefreshTokenFixture {

        val claimsOrError =
          authenticationService
            .refreshClaims(RefreshTokenRequest(existingAccount.email, "CAFEBABE"))
            .foldMap(refreshTokenOrAccountInterpreter)

        val error = claimsOrError.left.value
        error shouldBe InvalidToken
      }
    }

    it("should return error when account is banned") {
      new AccountFixture with RefreshTokenFixture {
        val token = createToken(evilUser.email)

        val claimsOrError = authenticationService
          .refreshClaims(RefreshTokenRequest(evilUser.email, token))
          .foldMap(refreshTokenOrAccountInterpreter)

        val error = claimsOrError.left.value
        error shouldBe InvalidToken
      }
    }

    it("should return error when account is unconfirmed") {
      new AccountFixture with RefreshTokenFixture {
        val token = createToken(unconfirmedUser.email)

        val claimsOrError = authenticationService
          .refreshClaims(RefreshTokenRequest(unconfirmedUser.email, token))
          .foldMap(refreshTokenOrAccountInterpreter)

        val error = claimsOrError.left.value
        error shouldBe InvalidToken
      }
    }

    it("should return error given expired token") {
      new AccountFixture with RefreshTokenFixture {
        val token = createExpiredToken(existingAccount.email, authenticationService.refreshTokenTtl)

        val claimsOrError = authenticationService
          .refreshClaims(RefreshTokenRequest(existingAccount.email, token))
          .foldMap(refreshTokenOrAccountInterpreter)

        val error = claimsOrError.left.value
        error shouldBe TokenExpired
      }
    }
  }

}
