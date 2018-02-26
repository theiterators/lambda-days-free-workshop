package pl.iterators.forum.services

import org.scalatest._
import pl.iterators.forum.domain.PasswordPolicies._
import pl.iterators.forum.domain._
import pl.iterators.forum.fixtures._
import pl.iterators.forum.services.AccountService._
import pl.iterators.forum.utils.{Leave, Modify}

class AccountServiceSpecs extends FunSpec with Matchers with EitherValues {

  val accountService = new AccountService {
    private val passwordMinLength = 4

    override val passwordPolicy = (password: PasswordPlain) =>
      for {
        _ <- mustBeOfLength(passwordMinLength)(password)
        _ <- mustContainLetter(password)
        _ <- mustContainUpper(password)
      } yield password
  }

  describe("create regular account") {
    it("should create an account") {
      new AccountFixture {
        val accountOrError =
          accountService
            .createRegular(AccountCreateRequest(Email("user34@forum.com"), PasswordPlain("GoodPass")))
            .foldMap(accountInterpreter)

        accountOrError should matchPattern {
          case Right(_) =>
        }
      }
    }

    it("should not give admin rights") {
      new AccountFixture {
        val accountOrError = accountService
          .createRegular(AccountCreateRequest(Email("user100@forum.com"), PasswordPlain("GoodPass")))
          .foldMap(accountInterpreter)
        accountOrError.right.value.isAdmin shouldEqual false
      }
    }
  }

  describe("create admin account") {
    it("should create an account") {
      new AccountFixture {
        val accountOrError =
          accountService
            .createAdmin(AdminAccountCreateRequest(Email("superUser@forum.com"), PasswordPlain("GoodPass"), Nick("Administrator")))
            .foldMap(accountInterpreter)

        accountOrError should matchPattern {
          case Right(_) =>
        }
      }
    }

    it("should give admin rights") {
      new AccountFixture {
        val accountOrError = accountService
          .createAdmin(AdminAccountCreateRequest(Email("superUser@forum.com"), PasswordPlain("GoodPass"), Nick("Administrator")))
          .foldMap(accountInterpreter)
        accountOrError.right.value.isAdmin shouldEqual true
      }
    }
  }

  describe("update account") {
    it("should perform all the changes") {
      new AccountFixture {
        val id = existingAccount.id
        val updatedOrError = accountService
          .update(id, AccountChangeRequest(about = Modify("About me"), password = Some(PasswordPlain("GoodPass"))))
          .foldMap(accountInterpreter)

        val updated = updatedOrError.right.value
        updated.id shouldEqual id
        updated.about shouldEqual Some("About me")
        updated.validatePassword("GoodPass") shouldEqual true
      }
    }

    it("should not perform changes on invalid account") {
      new AccountFixture {
        val updatedOrError = accountService
          .update(AccountId(1034), AccountChangeRequest(about = Modify("About me"), password = Some(PasswordPlain("GoodPass"))))
          .foldMap(accountInterpreter)

        updatedOrError.left.value shouldEqual AccountNotExists
      }
    }

    it("should not allow to change password if it is too weak") {
      new AccountFixture {
        val account = existingAccount
        val updatedOrError = accountService
          .update(account.id, AccountChangeRequest(about = Leave, password = Some(PasswordPlain("badpass"))))
          .foldMap(accountInterpreter)

        updatedOrError.left.value shouldEqual PasswordTooWeak
      }
    }
  }

}
