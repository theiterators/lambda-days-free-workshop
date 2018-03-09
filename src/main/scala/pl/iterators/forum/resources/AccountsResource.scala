package pl.iterators.forum.resources

import java.time.OffsetDateTime
import java.util.Locale

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Route
import pl.iterators.forum.domain._
import pl.iterators.forum.repositories.interpreters._
import pl.iterators.forum.services.AccountService
import pl.iterators.forum.services.AccountService._
import play.api.libs.functional.syntax._
import play.api.libs.json._

object AccountsResource {
  trait AccountsProtocol extends CommonJsonProtocol {
    import pl.iterators.forum.utils.Change._

    final val accountPath: AccountId => Path = id => Path("accounts") / id.toString
    implicit val accountWrites: OWrites[Account] =
      ((__ \ "email").write[Email] and
        (__ \ "nick").writeNullable[Nick] and
        (__ \ "about").writeNullable[String] and
        (__ \ "createdAt").write[OffsetDateTime] and
        (__ \ "isAdmin").write[Boolean] and
        (__ \ "banned").write[Boolean] and
        (__ \ "confirmed").write[Boolean])(account =>
        (account.email, account.nick, account.about, account.createdAt, account.isAdmin, account.isBanned, account.isConfirmed))

    implicit val createAccountRequestFormat: Format[AccountCreateRequest] = Json.format[AccountCreateRequest]
    implicit val changeAccountRequestReads: Reads[AccountChangeRequest] = (
      (__ \ "about").readChange[String] and
        (__ \ "password").readNullable[PasswordPlain]
    )(AccountChangeRequest.apply _)

  }

  private class MailEnv(override val locale: Locale, val host: String, val confirmationLinkTemplate: String) extends ConfirmationEmailEnv {
    override def confirmationLink(email: Email, token: ConfirmationToken) =
      confirmationLinkTemplate.format(host, token.value, Email.uriEncode(email))
  }
}

trait AccountsResource extends Resource with AccountsResource.AccountsProtocol with LanguageSupport {
  def accountService: AccountService
  def accountRepositoryInterpreter: AccountRepositoryInterpreter
  def confirmationTokenInterpreter: ConfirmationTokenRepositoryInterpreter
  def mailingInterpreter: MailingRepositoryInterpreter

  def confirmationLinkTemplate: String

  import cats.instances.future._

  private def runLookupAccount(id: AccountId) = accountService.lookup(id) foldMap accountRepositoryInterpreter
  protected val lookupAccount: AccountId => Route = id =>
    get {
      jwtAuthorize(authSingleAccount(id) | authAdmin) {
        onSuccess(runLookupAccount(id)) {
          case Some(account) => completeAsResource(account)(accountPath)
          case None          => complete(NotFound)
        }
      }
  }

  private def runQueryEmail(email: Email) = accountService.queryEmail(email) foldMap accountRepositoryInterpreter
  private def runQueryNick(nick: Nick)    = accountService.queryNick(nick) foldMap accountRepositoryInterpreter
  protected val queryAccount: Route = get {
    parameter('email.as[Email]) { email =>
      jwtAuthorize(authSingleAccount(email) | authAdmin) {
        onSuccess(runQueryEmail(email)) {
          case Some(account) => completeAsResource(account)(accountPath)
          case None          => complete(NotFound)
        }
      }
    } ~
      parameter('nick.as[Nick]) { nick =>
        jwtAuthorize(authAdmin) {
          onSuccess(runQueryNick(nick)) {
            case Some(account) => completeAsResource(account)(accountPath)
            case None          => complete(NotFound)
          }
        }
      }
  }

  private def runCheckNickExists(nick: Nick) =
    accountService.exists(nick) foldMap accountRepositoryInterpreter
  protected val checkNickExists: Route =
    (head & parameter('nick.as[Nick])) { nick =>
      onSuccess(runCheckNickExists(nick))(exists => if (exists) complete(NoContent) else complete(NotFound))
    }

  import pl.iterators.forum.resources.AccountsResource.MailEnv
  private def withMailEnv = extractHost.flatMap { host =>
    determineLocale().flatMap { locale =>
      provide(new MailEnv(locale, host, confirmationLinkTemplate))
    }
  }
  private def runCreateAccount(accountCreateRequest: AccountCreateRequest)(mailEnv: MailEnv) =
    accountService
      .createRegular(accountCreateRequest)
      .run(mailEnv) foldMap (mailingInterpreter or (accountRepositoryInterpreter or confirmationTokenInterpreter))
  protected val createAccount: Route = (post & entity(as[AccountCreateRequest])) { accountCreateRequest =>
    withMailEnv { mailEnv =>
      onSuccess(runCreateAccount(accountCreateRequest)(mailEnv)) {
        case Left(error)          => complete(Conflict -> error)
        case Right(accountWithId) => completeWithLocation(accountWithId)(accountPath)
      }
    }
  }

  private def runUpdateAccount(id: AccountId, accountChangeRequest: AccountChangeRequest) =
    accountService.update(id, accountChangeRequest) foldMap accountRepositoryInterpreter
  protected val updateAccount: AccountId => Route = id =>
    patch {
      jwtAuthorize(authSingleAccount(id)) {
        entity(as[AccountChangeRequest]) { accountChangeRequest =>
          onSuccess(runUpdateAccount(id, accountChangeRequest)) {
            case Left(AccountNotExists) => complete(NotFound)
            case Left(PasswordTooWeak)  => complete(BadRequest -> PasswordTooWeak)
            case Left(error)            => complete(Conflict -> error)
            case Right(accountWithId)   => completeAsResource(accountWithId)(accountPath)
          }
        }
      }
  }

  val accountsRoutes =
    pathEndOrSingleSlash {
      queryAccount ~ createAccount ~ checkNickExists
    } ~
      pathPrefix(IntNumber.map(AccountId(_))) { accountId =>
        pathEnd {
          lookupAccount(accountId) ~ updateAccount(accountId)
        }
      }
}
