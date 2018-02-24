package pl.iterators.forum.services

import org.scalatest._
import pl.iterators.forum.domain._
import pl.iterators.forum.fixtures.AccountFixture
import pl.iterators.forum.services.AuthenticationService.AuthRequest

class AuthenticationServiceSpecs extends FunSpec with Matchers with EitherValues with OptionValues {

  val authenticationService = new AuthenticationService {}

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

}
