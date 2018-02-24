package pl.iterators.forum.repositories

import cats.free.Free
import pl.iterators.forum.domain.{ConfirmationToken, Email, RefreshToken}

sealed trait RefreshTokenRepository[A]

object RefreshTokenRepository {
  case class Store(token: RefreshToken)         extends RefreshTokenRepository[RefreshToken]
  case class Query(email: Email, token: String) extends RefreshTokenRepository[Option[RefreshToken]]

  def store(token: RefreshToken)         = Free.liftF(Store(token))
  def query(email: Email, token: String) = Free.liftF(Query(email, token))
}

sealed trait ConfirmationTokenRepository[A]

object ConfirmationTokenRepository {
  case class Store(token: ConfirmationToken)    extends ConfirmationTokenRepository[ConfirmationToken]
  case class Query(email: Email, token: String) extends ConfirmationTokenRepository[Option[ConfirmationToken]]

  def store(token: ConfirmationToken)    = Free.liftF(Store(token))
  def query(email: Email, token: String) = Free.liftF(Query(email, token))
}
