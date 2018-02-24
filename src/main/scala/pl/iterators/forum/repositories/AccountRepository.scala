package pl.iterators.forum.repositories

import cats.free.Free
import pl.iterators.forum.domain._

sealed trait AccountRepository[A]

object AccountRepository {
  type StoreResult = Either[AccountError, AccountWithId]

  case class Lookup(id: AccountId)        extends AccountRepository[Option[AccountWithId]]
  case class QueryEmail(email: Email)     extends AccountRepository[Option[AccountWithId]]
  case class QueryConfirmed(email: Email) extends AccountRepository[Option[ConfirmedAccountWithId]]

  def lookup(id: AccountId)        = Free.liftF(Lookup(id))
  def queryEmail(email: Email)     = Free.liftF(QueryEmail(email))
  def queryConfirmed(email: Email) = Free.liftF(QueryConfirmed(email))

}
