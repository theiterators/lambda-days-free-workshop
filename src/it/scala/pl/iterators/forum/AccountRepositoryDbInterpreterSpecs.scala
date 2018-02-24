package pl.iterators.forum

import java.time.OffsetDateTime

import org.scalatest._
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
