package pl.iterators.forum.repositories.interpreters

import java.time.OffsetDateTime

import cats.instances.future._
import pl.iterators.forum.domain._
import pl.iterators.forum.domain.tags.{IdTag, NickTag}
import pl.iterators.forum.repositories.{AccountOperation, AccountRepository}
import pl.iterators.forum.utils.crypto.Crypto.Password
import pl.iterators.forum.utils.db.PostgresDriver.api._
import pl.iterators.forum.utils.db.Repository.{NotUpdated, UniqueViolation}
import pl.iterators.forum.utils.db._
import pl.iterators.forum.utils.tag.@@
import slick.dbio.Effect.{Read, Write}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AccountRepositoryDbInterpreter(db: Database)(implicit executionContext: ExecutionContext) extends AccountRepositoryInterpreter {
  import AccountRepository._
  import AccountRepositoryDbInterpreter._

  private val users = new Repository[Int @@ IdTag[Account], User, UsersTable](TableQuery[UsersTable])

  override def apply[A](fa: AccountRepository[A]): Future[A] = fa match {
    case Lookup(id)                      => db.run(lookup(id))
    case QueryEmail(email)               => db.run(queryEmail(email))
    case QueryConfirmed(email)           => db.run(queryConfirmed((email)))
    case QueryNick(nick)                 => db.run(queryNick(nick))
    case Exists(nick)                    => db.run(exists(nick))
    case Store(email, password, isAdmin) => db.run(store(email, password, isAdmin))
    case Update(id, f)                   => db.run(update(id, f))
  }

  final def apply[A](composite: AccountOperation[A]): Future[A] = composite foldMap this

  def lookup(id: AccountId): DBIOAction[Option[AccountWithId], NoStream, Read]    = users.find(id)
  def queryEmail(email: Email): DBIOAction[Option[AccountWithId], NoStream, Read] = filterEmailC(email).result.headOption

  def queryConfirmed(email: Email): DBIOAction[Option[ConfirmedAccountWithId], NoStream, Read] =
    filterEmailConfirmedC(email).result.headOption.map(_.collect(collectConfirmed))

  def queryNick(nick: Nick): DBIOAction[Option[ConfirmedAccountWithId], NoStream, Read] =
    filterNickC(nick).result.headOption.map(_.collect(collectConfirmed))
  def exists(nick: Nick): DBIOAction[Boolean, NoStream, Read] = existsNickC(nick).result

  def store(email: Email, password: Password, isAdmin: Boolean): DBIOAction[Either[EmailNotUnique.type, AccountWithId], NoStream, Write] =
    users
      .tryInsertAndReturn(
        User(email = email,
             nick = None,
             encryptedPassword = password,
             about = None,
             isAdmin = isAdmin,
             isBanned = false,
             isConfirmed = false,
             createdAt = OffsetDateTime.now()))
      .flatMap {
        case Success(user)                                  => DBIO.successful(Right(user))
        case Failure(UniqueViolation("users_email_key", _)) => DBIO.successful(Left(EmailNotUnique))
        case Failure(t)                                     => DBIO.failed(t)
      }
  def update(id: AccountId, f: User => Account): DBIOAction[Either[AccountDbError, AccountWithId], NoStream, Read with Write] =
    users.tryUpdate(id, f andThen User.fromAccount).flatMap {
      case Success(Right(user))                           => DBIO.successful(Right(user))
      case Success(Left(NotUpdated))                      => DBIO.successful(Left(AccountNotExists))
      case Failure(UniqueViolation("users_email_key", _)) => DBIO.successful(Left(EmailNotUnique))
      case Failure(UniqueViolation("users_nick_key", _))  => DBIO.successful(Left(NickNotUnique))
      case Failure(t)                                     => DBIO.failed(t)
    }

  private def filterEmail(email: Rep[Email]) = users.table.filter(_.email === email)
  private val filterEmailC                   = Compiled(filterEmail _)

  private def filterEmailConfirmed(email: Rep[Email]) = filterEmail(email).filter(_.confirmed)
  private val filterEmailConfirmedC                   = Compiled(filterEmailConfirmed _)

  private def filterNick(nick: Rep[String @@ NickTag]) = users.table.filter(_.nick.toLowerCase === nick.toLowerCase)
  private val filterNickC                              = Compiled(filterNick _)

  private def existsNick(nick: Rep[Nick]) = filterNick(nick).map(_.id).exists
  private val existsNickC                 = Compiled(existsNick _)

}

object AccountRepositoryDbInterpreter extends TypeMappers {
  case class User(email: Email,
                  nick: Option[Nick],
                  encryptedPassword: Password,
                  about: Option[String],
                  isAdmin: Boolean,
                  isBanned: Boolean,
                  isConfirmed: Boolean,
                  createdAt: OffsetDateTime)
      extends Account {
    override def withPassword(newPassword: Password) = copy(encryptedPassword = newPassword)
    override def withAbout(newAbout: Option[String]) = copy(about = newAbout)
  }
  object User {
    def fromAccount(account: Account): User = account match {
      case user: User => user
      case _ =>
        User(
          email = account.email,
          nick = account.nick,
          encryptedPassword = account.encryptedPassword,
          about = account.about,
          isAdmin = account.isAdmin,
          isBanned = account.isBanned,
          isConfirmed = account.isConfirmed,
          createdAt = account.createdAt
        )
    }

    val tupled = (apply _).tupled
  }
  case class ConfirmedUser(user: User, confirmedNick: Nick) extends ConfirmedAccount {
    require(user.isConfirmed)

    override val email             = user.email
    override val about             = user.about
    override val encryptedPassword = user.encryptedPassword
    override val createdAt         = user.createdAt
    override val isAdmin           = user.isAdmin
    override val isBanned          = user.isBanned

    override def withPassword(newPassword: Password) = copy(user = user.withPassword(newPassword))
    override def withAbout(newAbout: Option[String]) = copy(user = user.withAbout(newAbout))
  }

  final class UsersTable(tag: slick.lifted.Tag) extends TableWithId[AccountId, User](tag, "users") {

    def email             = column[Email]("email", O.Unique)
    def nick              = column[Option[String @@ NickTag]]("nick", O.Unique)
    def encryptedPassword = column[Password]("encrypted_password")
    def about             = column[Option[String]]("about")
    def isAdmin           = column[Boolean]("admin")
    def banned            = column[Boolean]("banned")
    def confirmed         = column[Boolean]("confirmed")
    def createdAt         = column[OffsetDateTime]("created_at")

    override val model = (email, nick, encryptedPassword, about, isAdmin, banned, confirmed, createdAt) <> (User.tupled, User.unapply)
  }

  val collectConfirmed: PartialFunction[WithId[AccountId, User], WithId[AccountId, ConfirmedUser]] = {
    case entity @ WithId(_, User(_, Some(nick), _, _, _, _, true, _)) => entity.transform(ConfirmedUser(_, nick))
  }

}
