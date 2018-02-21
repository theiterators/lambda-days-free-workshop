package pl.iterators.forum

import org.scalatest._
import pl.iterators.forum.domain.tags._
import pl.iterators.forum.fixtures.Accounts
import pl.iterators.forum.repositories.interpreters.AccountRepositoryDbInterpreter
import pl.iterators.forum.utils.tag._

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

}
