package pl.iterators.forum

import java.time.OffsetDateTime

import org.scalatest._
import pl.iterators.forum.domain.{AccountId, AccountNotExists, EmailNotUnique}
import pl.iterators.forum.domain.tags._
import pl.iterators.forum.fixtures.Accounts
import pl.iterators.forum.repositories.interpreters.AccountRepositoryDbInterpreter
import pl.iterators.forum.repositories.interpreters.AccountRepositoryDbInterpreter.User
import pl.iterators.forum.utils.crypto.Crypto
import pl.iterators.forum.utils.tag._

import scala.util.Random

class AccountRepositoryDbInterpreterSpecs extends BaseItSpec with OptionValues with EitherValues with RecoverMethods {
  def accountRepository = new AccountRepositoryDbInterpreter(db)

  it should "lookup user" in {
    new Accounts(db)
      .fixture("user@forum.com", "JohnnyBGood", "anything") { id =>
        accountRepository.lookup(id)
      }
      .map(_.value)
      .map { user =>
        user.email shouldEqual "user@forum.com"
        user.nick shouldEqual Some("JohnnyBGood")
        user.validatePassword("anything") shouldBe true
      }
  }

  it should "query email" in {
    new Accounts(db)
      .fixture("user@forum.com", "JohnnyBGood", "anything") { _ =>
        accountRepository.queryEmail("user@forum.com".@@[EmailTag])
      }
      .map(_.value)
      .map { user =>
        user.email shouldEqual "user@forum.com"
        user.nick shouldEqual Some("JohnnyBGood")
        user.validatePassword("anything") shouldBe true
      }
  }

  it should "query confirmed" in {
    new Accounts(db)
      .fixture(unconfirmedUser("unconfirmed_user@forum.com"), confirmedUser("user@forum.com", "Joe")) {
        for {
          confirmed   <- accountRepository.queryConfirmed("user@forum.com".@@[EmailTag])
          unconfirmed <- accountRepository.queryConfirmed("unconfirmed_user@forum.com".@@[EmailTag])
        } yield (confirmed, unconfirmed)
      }
      .map {
        case (maybeConfirmed, maybeUnconfirmed) =>
          val confirmed = maybeConfirmed.value
          confirmed.email shouldEqual "user@forum.com"
          confirmed.nick shouldEqual Some("Joe")

          maybeUnconfirmed shouldBe empty
      }
  }

  it should "query nick" in {
    new Accounts(db)
      .fixture("user@forum.com", "JohnnyBGood", "anything") { _ =>
        accountRepository.queryNick("JohnnyBGood".@@[NickTag])
      }
      .map(_.value)
      .map { user =>
        user.email shouldEqual "user@forum.com"
        user.nick shouldEqual Some("JohnnyBGood")
        user.validatePassword("anything") shouldBe true
      }
  }

  it should "store user" in {
    new Accounts(db)
      .withRollback {
        for {
          _       <- accountRepository.store("user@forum.com".@@[EmailTag], Crypto.encryptFunction("password"), isAdmin = false)
          account <- accountRepository.queryEmail("user@forum.com".@@[EmailTag])
        } yield account
      }
      .map(_.value)
      .map { user =>
        user.email shouldEqual "user@forum.com"
        user.nick shouldBe empty
        user.validatePassword("password") shouldBe true
        user.isAdmin shouldBe false
        user.isConfirmed shouldBe false
        user.isBanned shouldBe false
      }

  }

  it should "maintain email uniqueness" in {
    new Accounts(db)
      .fixture("user@forum.com", "JohnnyBGood", "anything") { _ =>
        accountRepository.store("user@forum.com".@@[EmailTag], Crypto.encryptFunction("password"), isAdmin = false)
      }
      .map(_ shouldEqual Left(EmailNotUnique))
  }

  it should "update user" in {
    new Accounts(db)
      .fixture("user@forum.com", "JohnnyBGood", "anything") { userId =>
        accountRepository.update(userId, _.copy(about = Some("I am a simple man"), isAdmin = true, isConfirmed = true))
      }
      .map(_.right.value)
      .map { user =>
        user.email shouldEqual "user@forum.com"
        user.nick shouldEqual Some("JohnnyBGood")
        user.validatePassword("anything") shouldBe true
        user.about shouldEqual Some("I am a simple man")
        user.isAdmin shouldEqual true
        user.isConfirmed shouldEqual true
      }
  }

  it should "signal error when updating non-existent user" in {
    new Accounts(db)
      .withRollback {
        accountRepository.update(AccountId(10), identity)
      }
      .map(_ shouldEqual Left(AccountNotExists))
  }

  it should "check if nick exists" in {
    new Accounts(db)
      .fixture(confirmedUser("user1@example.com", "User 1"),
               confirmedUser("user2@example.com", "User 2"),
               confirmedUser("user3@example.com", "User 3")) {
        for {
          existing     <- accountRepository.exists("User 1".@@[NickTag])
          alsoExisting <- accountRepository.exists("user 1".@@[NickTag])
          notExisting  <- accountRepository.exists("Sancho".@@[NickTag])
        } yield (existing, alsoExisting, notExisting)
      }
      .map {
        case (existing, alsoExisting, notExisting) =>
          existing shouldBe true
          alsoExisting shouldBe true
          notExisting shouldBe false
      }
  }

  def confirmedUser(email: String,
                    nick: String,
                    password: String = Random.nextString(10),
                    nowValue: OffsetDateTime = OffsetDateTime.now()) = User(
    email = email.@@[EmailTag],
    nick = Some(nick.@@[NickTag]),
    about = None,
    encryptedPassword = Crypto.encryptFunction(password),
    createdAt = nowValue,
    isAdmin = false,
    isBanned = false,
    isConfirmed = true
  )
  def unconfirmedUser(email: String, password: String = Random.nextString(13), nowValue: OffsetDateTime = OffsetDateTime.now()) = User(
    email = email.@@[EmailTag],
    nick = None,
    about = None,
    encryptedPassword = Crypto.encryptFunction(password),
    createdAt = nowValue,
    isAdmin = false,
    isBanned = false,
    isConfirmed = false
  )

}
