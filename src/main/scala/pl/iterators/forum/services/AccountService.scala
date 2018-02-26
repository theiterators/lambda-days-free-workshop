package pl.iterators.forum.services

import cats.data.EitherT
import pl.iterators.forum.domain._
import pl.iterators.forum.repositories.AccountRepository.StoreResult
import pl.iterators.forum.repositories._

import pl.iterators.forum.utils.Change
import pl.iterators.forum.utils.crypto.Crypto

trait AccountService {
  import pl.iterators.forum.services.AccountService._

  def passwordPolicy: PasswordPlain => Either[PasswordTooWeak.type, String]

  def queryEmail(email: Email): AccountOperation[Option[AccountWithId]] = AccountRepository.queryEmail(email)
  def lookup(id: AccountId): AccountOperation[Option[AccountWithId]]    = AccountRepository.lookup(id)

  def createRegular(accountCreateRequest: AccountCreateRequest): AccountOperation[Either[AccountError, AccountWithId]] =
    store(accountCreateRequest, admin = false)
  def createAdmin(adminAccountCreateRequest: AdminAccountCreateRequest): AccountOperation[Either[AccountError, AccountWithId]] =
    store(adminAccountCreateRequest.asAccountCreateRequest, admin = true)
  private def store(accountCreateRequest: AccountCreateRequest, admin: Boolean): AccountOperation[Either[AccountError, AccountWithId]] = {
    val action = for {
      _       <- EitherT.fromEither[AccountOperation](passwordPolicy(accountCreateRequest.password))
      account <- EitherT(AccountRepository.store(accountCreateRequest.email, Crypto.encryptFunction(accountCreateRequest.password), admin))
    } yield account

    action.value
  }

  def update(id: AccountId, accountChangeRequest: AccountChangeRequest): AccountOperation[StoreResult] =
    (for {
      _       <- EitherT.fromEither[AccountOperation](accountChangeRequest.validatePassword(passwordPolicy))
      updated <- EitherT(AccountRepository.update(id, accountChangeRequest.updateFunction))
    } yield updated).value

  def queryNick(nick: Nick): AccountOperation[Option[ConfirmedAccountWithId]] = AccountRepository.queryNick(nick)
  def exists(nick: Nick): AccountOperation[Boolean]                           = AccountRepository.exists(nick)
}

object AccountService {
  case class AccountCreateRequest(email: Email, password: PasswordPlain)
  case class AdminAccountCreateRequest(email: Email, password: PasswordPlain, nick: Nick) {
    def asAccountCreateRequest = AccountCreateRequest(email, password)
  }
  case class AccountChangeRequest(about: Change[String], password: Option[PasswordPlain]) {
    def validatePassword(passwordPolicy: PasswordPlain => Either[PasswordTooWeak.type, String]): Either[PasswordTooWeak.type, Ok.type] =
      password.fold[Either[PasswordTooWeak.type, Ok.type]](Right(Ok))(password => passwordPolicy(password).map(_ => Ok))

    private def updatePassword(account: Account): Account = password.fold(account)(newPassword => account.withPassword(newPassword.encrypt))
    private def updateAbout(account: Account): Account    = about.fold(account)(newAbout => account.withAbout(newAbout))
    val updateFunction: Account => Account                = updatePassword _ compose updateAbout
  }
}
