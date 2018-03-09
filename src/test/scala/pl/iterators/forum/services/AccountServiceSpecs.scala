package pl.iterators.forum.services

import org.scalatest._
import pl.iterators.forum.domain.PasswordPolicies._
import pl.iterators.forum.domain._
import pl.iterators.forum.fixtures._
import pl.iterators.forum.services.AccountService._
import pl.iterators.forum.utils.{Leave, Modify}

class AccountServiceSpecs extends FunSpec with Matchers with EitherValues with LoneElement {

  val accountService = new AccountService {
    private val passwordMinLength = 4

    override val passwordPolicy = (password: PasswordPlain) =>
      for {
        _ <- mustBeOfLength(passwordMinLength)(password)
        _ <- mustContainLetter(password)
        _ <- mustContainUpper(password)
      } yield password

    override val messages = new Messages {
      override val from = EmailAddress(Email("noreply@example.com"), name = Some("no-reply"))
    }
  }

  describe("create regular account") {
    it("should create an account") {
      new AccountFixture with ConfirmationTokenFixture with MailingFixture {
        val accountOrError =
          accountService
            .createRegular(AccountCreateRequest(Email("user34@forum.com"), PasswordPlain("GoodPass")))
            .run(confirmationEmailEnv)
            .foldMap(nullInterpreter or confirmationTokenOrAccountInterpreter)

        accountOrError should matchPattern {
          case Right(_) =>
        }
      }
    }

    it("should not give admin rights") {
      new AccountFixture with ConfirmationTokenFixture with MailingFixture {
        val accountOrError = accountService
          .createRegular(AccountCreateRequest(Email("user100@forum.com"), PasswordPlain("GoodPass")))
          .run(confirmationEmailEnv)
          .foldMap(nullInterpreter or confirmationTokenOrAccountInterpreter)
        accountOrError.right.value.isAdmin shouldEqual false
      }
    }

    it("should create confirmation token for a new account") {
      new AccountFixture with ConfirmationTokenFixture with MailingFixture {
        accountService
          .createRegular(AccountCreateRequest(Email("user20@example.com"), PasswordPlain("Dr56::sf")))
          .run(confirmationEmailEnv)
          .foldMap(nullInterpreter or confirmationTokenOrAccountInterpreter)

        val token = tokenInterpreter.find(Email("user20@example.com")).loneElement
        token.email shouldEqual "user20@example.com"
      }
    }

    it("should not create confirmation token if account is not created") {
      new AccountFixture with ConfirmationTokenFixture with MailingFixture {
        accountService
          .createRegular(AccountCreateRequest(Email("user20@example.com"), PasswordPlain("badpass")))
          .run(confirmationEmailEnv)
          .foldMap(nullInterpreter or confirmationTokenOrAccountInterpreter)

        val tokens = tokenInterpreter.find(Email("user20@example.com"))
        tokens shouldBe empty
      }
    }

    it("should send confirmation email") {
      new AccountFixture with ConfirmationTokenFixture with MailingFixture {
        import cats.instances.list._

        val log = accountService
          .createRegular(AccountCreateRequest(Email("user20@example.com"), PasswordPlain("Dr56::sf")))
          .run(confirmationEmailEnv)
          .foldMap(mailingLogger or (confirmationTokenOrAccountInterpreter andThen writeEmailLogValue))

        val emails: List[EmailMessage] = log.written

        val email = emails.loneElement
        val token = tokenInterpreter.find(Email("user20@example.com")).loneElement

        email should matchPattern {
          case accountService.messages.ConfirmationMessage(address, _, link)
              if address == "user20@example.com" && link == confirmationEmailEnv.confirmationLink(Email("user20@example.com"), token) =>
        }
      }
    }

    it("should not send confirmation email if account is not created") {
      new AccountFixture with ConfirmationTokenFixture with MailingFixture {
        import cats.instances.list._
        val log = accountService
          .createRegular(AccountCreateRequest(Email("user20@example.com"), PasswordPlain("badpass")))
          .run(confirmationEmailEnv)
          .foldMap(mailingLogger or (confirmationTokenOrAccountInterpreter andThen writeEmailLogValue))

        val emails = log.written
        emails shouldBe empty
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
