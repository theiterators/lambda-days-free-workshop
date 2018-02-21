package pl.iterators.forum.resources

import java.time.OffsetDateTime

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Route
import pl.iterators.forum.domain._
import pl.iterators.forum.repositories.interpreters._
import pl.iterators.forum.services.AccountService
import play.api.libs.functional.syntax._
import play.api.libs.json._

object AccountsResource {
  trait AccountsProtocol extends CommonJsonProtocol {
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

  }
}

trait AccountsResource extends Resource with AccountsResource.AccountsProtocol with LanguageSupport {
  def accountService: AccountService
  def accountRepositoryInterpreter: AccountRepositoryInterpreter

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

  protected val queryAccount: Route = get {
    parameter('email.as[Email]) { email =>
      jwtAuthorize(authSingleAccount(email) | authAdmin) {
        onSuccess(runQueryEmail(email)) {
          case Some(account) => completeAsResource(account)(accountPath)
          case None          => complete(NotFound)
        }
      }
    }
  }

  val accountsRoutes =
    pathEndOrSingleSlash {
      queryAccount
    } ~
      pathPrefix(IntNumber.map(AccountId(_))) { accountId =>
        pathEnd {
          lookupAccount(accountId)
        }
      }
}
