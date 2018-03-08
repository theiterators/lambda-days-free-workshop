package pl.iterators.forum.services

import cats.InjectK
import cats.data.EitherT
import cats.free.Free
import pl.iterators.forum.domain._
import pl.iterators.forum.repositories.AccountRepository.StoreResult
import pl.iterators.forum.repositories._
import pl.iterators.forum.utils.free.syntax._
import pl.iterators.forum.utils.Change
import pl.iterators.forum.utils.crypto.Crypto

import scala.language.higherKinds

trait AccountService {
  import pl.iterators.forum.services.AccountService._

  def passwordPolicy: PasswordPlain => Either[PasswordTooWeak.type, String]

  def queryEmail(email: Email): AccountOperation[Option[AccountWithId]] = AccountRepository.queryEmail(email)
  def lookup(id: AccountId): AccountOperation[Option[AccountWithId]]    = AccountRepository.lookup(id)

  def createRegular(
      accountCreateRequest: AccountCreateRequest): ConfirmationTokenWithAccountOperation[Either[AccountError, AccountWithId]] = {
    import ConfirmationTokenOrAccount._

    val storeAccountAndConfirmationToken = for {
      account <- EitherT(store[ConfirmationTokenOrAccount](accountCreateRequest, admin = false))
      confirmationToken = ConfirmationToken.generate(account.email)
      _ <- storeToken(confirmationToken).toEitherT[AccountError]
    } yield account

    storeAccountAndConfirmationToken.value
  }
  def createAdmin(adminAccountCreateRequest: AdminAccountCreateRequest): AccountOperation[Either[AccountError, AccountWithId]] =
    store(adminAccountCreateRequest.asAccountCreateRequest, admin = true)

  def update(id: AccountId, accountChangeRequest: AccountChangeRequest): AccountOperation[StoreResult] =
    EitherT(Free.defer(accountChangeRequest.validatePassword(passwordPolicy).pure[AccountRepository]))
      .flatMapF(_ => AccountRepository.update(id, accountChangeRequest.updateFunction))
      .value

  def queryNick(nick: Nick): AccountOperation[Option[ConfirmedAccountWithId]] = AccountRepository.queryNick(nick)
  def exists(nick: Nick): AccountOperation[Boolean]                           = AccountRepository.exists(nick)

  private def store[F[_]](accountCreateRequest: AccountCreateRequest, admin: Boolean)(
    implicit inj: InjectK[AccountRepository, F]): Free[F, Either[AccountError, AccountWithId]] =
    EitherT(Free.defer(passwordPolicy(accountCreateRequest.password).pure[F]))
      .flatMapF(password => AccountRepository.store(accountCreateRequest.email, Crypto.encryptFunction(password), admin))
      .value

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
