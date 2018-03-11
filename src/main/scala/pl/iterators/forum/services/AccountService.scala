package pl.iterators.forum.services

import java.time.Duration
import java.util.Locale

import cats.InjectK
import cats.data._
import cats.free.Free
import pl.iterators.forum.domain._
import pl.iterators.forum.repositories.AccountRepository.{Accounts, StoreResult}
import pl.iterators.forum.repositories.ConfirmationTokenRepository.ConfirmationTokens
import pl.iterators.forum.repositories.MailingRepository.Mailing
import pl.iterators.forum.repositories._
import pl.iterators.forum.utils.Change
import pl.iterators.forum.utils.crypto.Crypto
import pl.iterators.forum.utils.free.syntax._

import scala.language.higherKinds

trait AccountService {
  import pl.iterators.forum.services.AccountService._

  def passwordPolicy: PasswordPlain => Either[PasswordTooWeak.type, String]
  def messages: Messages
  def confirmationTokenTtl: Duration

  def queryEmail(email: Email): AccountOperation[Option[AccountWithId]] = AccountRepository.queryEmail(email)
  def lookup(id: AccountId): AccountOperation[Option[AccountWithId]]    = AccountRepository.lookup(id)

  def createRegular(accountCreateRequest: AccountCreateRequest)
    : Kleisli[RegistrationModule.Operation, ConfirmationEmailEnv, Either[AccountError, AccountWithId]] = {
    import RegistrationModule._
    import storage._

    storeAccount(accountCreateRequest, admin = false)(accounts)
      .assumeT[ConfirmationEmailEnv]
      .onSuccessRun(account => storeAndSendConfirmationToken(account)(confirmationTokens, mailing).map(_ => account))
  }
  def createAdmin(adminAccountCreateRequest: AdminAccountCreateRequest): AccountOperation[Either[AccountError, AccountWithId]] =
    storeAccount(adminAccountCreateRequest.asAccountCreateRequest, admin = true)

  def update(id: AccountId, accountChangeRequest: AccountChangeRequest): AccountOperation[StoreResult] =
    EitherT(Free.defer(accountChangeRequest.validatePassword(passwordPolicy).pure[AccountRepository]))
      .flatMapF(_ => AccountRepository.update(id, accountChangeRequest.updateFunction))
      .value

  def queryNick(nick: Nick): AccountOperation[Option[ConfirmedAccountWithId]] = AccountRepository.queryNick(nick)
  def exists(nick: Nick): AccountOperation[Boolean]                           = AccountRepository.exists(nick)

  def confirm(accountConfirmRequest: AccountConfirmRequest): AccountStoreModule.Operation[Either[ConfirmationError, Ok.type]] = {
    val accountStoreModule = AccountStoreModule()
    import accountStoreModule._

    confirmationTokens
      .query(accountConfirmRequest.email, accountConfirmRequest.confirmationToken)
      .toEither(ifNone = InvalidToken)
      .subflatMap(confirmationToken =>
        Either.cond(test = !confirmationToken.isExpired(confirmationTokenTtl), accountConfirmRequest.email, TokenExpired))
      .flatMapF(email => accounts.setConfirmed(email, accountConfirmRequest.nick))
      .value
  }

  def sendNewConfirmation(newConfirmationRequest: NewConfirmationRequest)
    : Kleisli[RegistrationModule.Operation, ConfirmationEmailEnv, Either[InvalidCredentials.type, Ok.type]] = {
    import RegistrationModule._
    import storage._

    accounts
      .queryEmail(newConfirmationRequest.email)
      .toEither(ifNone = InvalidCredentials)
      .subflatMap(account => Either.cond(test = !account.isConfirmed, account, InvalidCredentials))
      .subflatMap(account => Either.cond(test = account.validatePassword(newConfirmationRequest.password), account, InvalidCredentials))
      .assume[ConfirmationEmailEnv]
      .onSuccessRun(account => storeAndSendConfirmationToken(account)(confirmationTokens, mailing))
  }

  private def storeAccount[F[_]](accountCreateRequest: AccountCreateRequest, admin: Boolean)(
      implicit accounts: AccountRepository.Accounts[F]): Free[F, Either[AccountError, AccountWithId]] =
    EitherT(Free.defer(passwordPolicy(accountCreateRequest.password).pure[F]))
      .flatMapF(password => accounts.store(accountCreateRequest.email, Crypto.encryptFunction(password), admin))
      .value

  private def storeAndSendConfirmationToken[F[_]](account: Account)(
      confirmationTokens: ConfirmationTokens[F],
      mailing: Mailing[F]): Kleisli[Free[F, ?], ConfirmationEmailEnv, Ok.type] = Kleisli { (env: ConfirmationEmailEnv) =>
    val email = account.email
    for {
      confirmationToken <- Free.defer(ConfirmationToken.generate(email).pure[F])
      _                 <- confirmationTokens.store(confirmationToken)
      message = messages.ConfirmationMessage(email, env.locale, env.confirmationLink(email, confirmationToken))
      _ <- mailing.sendEmail(message)
    } yield Ok
  }

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
  case class AccountConfirmRequest(email: Email, confirmationToken: String, nick: Nick)

  type NewConfirmationRequest = AccountCreateRequest

  class AccountStoreModule[F[_]](implicit inj1: InjectK[AccountRepository, F], inj2: InjectK[ConfirmationTokenRepository, F]) {
    type Algebra[A] = F[A]

    val accounts: Accounts[F]                     = Accounts()
    val confirmationTokens: ConfirmationTokens[F] = ConfirmationTokens()
  }
  object AccountStoreModule {
    type Algebra[A]   = EitherK[AccountRepository, ConfirmationTokenRepository, A]
    type Operation[A] = Free[Algebra, A]
    def apply() = new AccountStoreModule[Algebra]()
  }

  object RegistrationModule {
    type Algebra[A]   = EitherK[MailingRepository, AccountStoreModule.Algebra, A]
    type Operation[A] = Free[Algebra, A]

    val storage: AccountStoreModule[Algebra] = new AccountStoreModule[Algebra]()
    val mailing: Mailing[Algebra]            = Mailing()
  }

  trait ConfirmationEmailEnv {
    def locale: Locale
    def confirmationLink(email: Email, token: ConfirmationToken): String
  }

}
