package pl.iterators.forum.fixtures

import java.time.OffsetDateTime

import pl.iterators.forum.domain.tags._
import pl.iterators.forum.domain.{Account, AccountId}
import pl.iterators.forum.repositories.interpreters.AccountRepositoryDbInterpreter._
import pl.iterators.forum.utils.crypto.Crypto
import pl.iterators.forum.utils.db.PostgresDriver.api._
import pl.iterators.forum.utils.db.Repository
import pl.iterators.forum.utils.tag._

import scala.concurrent.{ExecutionContext, Future}

class Accounts(db: Database) extends Base(db) {

  private val repository = new Repository[Int @@ IdTag[Account], User, UsersTable](TableQuery[UsersTable])
  def fixture[A](email: String, nick: String, password: String, admin: Boolean = false, banned: Boolean = false)(
      test: AccountId => DBIO[A])(implicit ec: ExecutionContext): Future[A] =
    fixture(email, Some(nick), password, admin, banned, confirmed = true)(test)
  def fixture[A](email: String, maybeNick: Option[String], password: String, admin: Boolean, banned: Boolean, confirmed: Boolean)(
      test: AccountId => DBIO[A])(implicit ec: ExecutionContext): Future[A] = withRollback {
    repository
      .insert(
        User(
          email = email.@@[EmailTag],
          nick = maybeNick.map(_.@@[NickTag]),
          about = None,
          encryptedPassword = Crypto.encryptFunction(password),
          createdAt = OffsetDateTime.now(),
          isAdmin = admin,
          isBanned = banned,
          isConfirmed = confirmed
        ))
      .flatMap(test)
  }
  def fixture[A](user: User)(test: AccountId => DBIO[A])(implicit ec: ExecutionContext): Future[A] = withRollback {
    repository.insert(user).flatMap(test)
  }
  def fixture[A](user: User, restOfUsers: User*)(test: => DBIO[A])(implicit ec: ExecutionContext): Future[A] = withRollback {
    repository.insertBatch(user +: restOfUsers).flatMap(_ => test)
  }

}
