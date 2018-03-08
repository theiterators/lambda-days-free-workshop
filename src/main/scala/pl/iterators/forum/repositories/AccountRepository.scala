package pl.iterators.forum.repositories

import cats.InjectK
import cats.free.Free
import pl.iterators.forum.domain._
import pl.iterators.forum.utils.crypto.Crypto.Password
import pl.iterators.forum.utils.free.syntax._

import scala.language.higherKinds

sealed trait AccountRepository[A]

object AccountRepository {
  type StoreResult = Either[AccountError, AccountWithId]

  case class Lookup(id: AccountId)                                   extends AccountRepository[Option[AccountWithId]]
  case class QueryEmail(email: Email)                                extends AccountRepository[Option[AccountWithId]]
  case class QueryConfirmed(email: Email)                            extends AccountRepository[Option[ConfirmedAccountWithId]]
  case class QueryNick(nick: Nick)                                   extends AccountRepository[Option[ConfirmedAccountWithId]]
  case class Exists(nick: Nick)                                      extends AccountRepository[Boolean]
  case class Store(email: Email, password: Password, admin: Boolean) extends AccountRepository[StoreResult]
  case class Update(id: AccountId, f: Account => Account)            extends AccountRepository[StoreResult]

  def lookup(id: AccountId)                        = Free.liftF(Lookup(id))
  def queryEmail(email: Email)                     = Free.liftF(QueryEmail(email))
  def queryConfirmed(email: Email)                 = Free.liftF(QueryConfirmed(email))
  def queryNick(nick: Nick)                        = Free.liftF(QueryNick(nick))
  def exists(nick: Nick)                           = Free.liftF(Exists(nick))
  def update(id: AccountId, f: Account => Account) = Free.liftF(Update(id, f))
  def store[F[_]](email: Email, password: Password, admin: Boolean)(implicit inj: InjectK[AccountRepository, F]) =
    Store(email, password, admin).into[F]

}
