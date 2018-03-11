package pl.iterators.forum.fixtures

import java.time.OffsetDateTime

import cats.{Id, ~>}
import pl.iterators.forum.domain._
import pl.iterators.forum.repositories.AccountRepository
import pl.iterators.forum.utils.crypto.Crypto
import pl.iterators.forum.utils.db.WithId

class AccountFixture {
  import AccountRepository._
  import AccountFixture._

  val accountInterpreter = new (AccountRepository ~> Id) {
    override def apply[A](fa: AccountRepository[A]) = fa match {
      case Lookup(id)            => lookup(id)
      case QueryEmail(email)     => queryEmail(email)
      case QueryConfirmed(email) => queryConfirmed(email)
      case QueryNick(nick)       => queryNick(nick)
      case Exists(nick)          => queryNick(nick).isDefined
      case Store(email, password, isAdmin) =>
        if (queryEmail(email).isDefined) Left(EmailNotUnique)
        else
          Right(
            WithId(
              AccountId(1),
              AccountImpl(email, None, password, nowValue, isAdmin = isAdmin)
            ))
      case Update(id, f) => lookup(id).fold[StoreResult](Left(AccountNotExists))(account => Right(account.transform(f)))
      case SetConfirmed(email, nick) =>
        if (queryEmail(email).isEmpty) Left(AccountNotExists)
        else if (queryNick(nick).isDefined) Left(NickNotUnique)
        else Right(Ok)
    }
    private def queryNick(nick: Nick) = {
      if (nick == existingAccount.confirmedNick) Some(existingAccount)
      else if (nick == adminAccount.confirmedNick) Some(adminAccount)
      else if (nick == evilUser.confirmedNick) Some(evilUser)
      else None
    }
    private def queryConfirmed(email: Email) = {
      if (email == existingAccount.email) Some(existingAccount)
      else if (email == adminAccount.email) Some(adminAccount)
      else if (email == evilUser.email) Some(evilUser)
      else None
    }
    private def queryEmail(email: Email) = queryConfirmed(email).orElse(if (email == unconfirmedUser.email) Some(unconfirmedUser) else None)
    private def lookup(id: AccountId) = {
      if (id == existingAccount.id) Some(existingAccount)
      else if (id == adminAccount.id) Some(adminAccount)
      else if (id == evilUser.id) Some(evilUser)
      else if (id == unconfirmedUser.id) Some(unconfirmedUser)
      else None
    }
  }

  lazy val nowValue: OffsetDateTime = OffsetDateTime.now()

  val existingAccountPlainPassword = "A1S2D3F4"
  val existingAccount: ConfirmedAccountWithId = WithId(
    AccountId(243784),
    ConfirmedAccountImpl(Nick("John Doe"),
                         Email("user@forum.com"),
                         None,
                         Crypto.encryptFunction(existingAccountPlainPassword),
                         nowValue,
                         isAdmin = false,
                         isBanned = false)
  )

  val adminPlainPassword = "Iteratorz!"
  val adminAccount: ConfirmedAccountWithId = WithId(
    AccountId(100),
    ConfirmedAccountImpl(Nick("Master Of The Universe"),
                         Email("admin@forum.com"),
                         None,
                         Crypto.encryptFunction(adminPlainPassword),
                         nowValue,
                         isAdmin = true,
                         isBanned = false)
  )

  val evilUserPassword = "faf!e35f"
  val evilUser: ConfirmedAccountWithId = WithId(
    AccountId(666),
    ConfirmedAccountImpl(Nick("Skeletor"),
                         Email("evil_user@forum.com"),
                         None,
                         Crypto.encryptFunction(evilUserPassword),
                         nowValue,
                         isAdmin = false,
                         isBanned = true)
  )

  val unconfirmedUserPassword = "ad343v !"
  val unconfirmedUser: AccountWithId = WithId(
    AccountId(324532),
    AccountImpl(Email("wannabe@user.com"), None, Crypto.encryptFunction(unconfirmedUserPassword), nowValue)
  )

}

object AccountFixture {
  case class ConfirmedAccountImpl(confirmedNick: Nick,
                                  email: Email,
                                  about: Option[String],
                                  encryptedPassword: Crypto.Password,
                                  createdAt: OffsetDateTime,
                                  isAdmin: Boolean,
                                  isBanned: Boolean)
      extends ConfirmedAccount {
    override def withPassword(newPassword: Crypto.Password) = this.copy(encryptedPassword = newPassword)
    override def withAbout(newAbout: Option[String])        = this.copy(about = newAbout)
  }

  case class AccountImpl(email: Email,
                         about: Option[String],
                         encryptedPassword: Crypto.Password,
                         createdAt: OffsetDateTime,
                         isAdmin: Boolean = false)
      extends Account {
    override def withPassword(newPassword: Crypto.Password) = this.copy(encryptedPassword = newPassword)
    override def withAbout(newAbout: Option[String])        = this.copy(about = newAbout)

    override val nick        = None
    override val isConfirmed = false
    override val isBanned    = false
  }
}
