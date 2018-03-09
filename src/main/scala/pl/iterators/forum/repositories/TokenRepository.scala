package pl.iterators.forum.repositories

import cats.InjectK
import cats.free.Free
import pl.iterators.forum.domain.{ConfirmationToken, Email, RefreshToken}
import pl.iterators.forum.utils.free.syntax._

import scala.language.higherKinds

sealed trait RefreshTokenRepository[A]

object RefreshTokenRepository {
  case class Store(token: RefreshToken)         extends RefreshTokenRepository[RefreshToken]
  case class Query(email: Email, token: String) extends RefreshTokenRepository[Option[RefreshToken]]

  def store(token: RefreshToken)         = Free.liftF(Store(token))
  def query(email: Email, token: String) = Free.liftF(Query(email, token))

  class RefreshTokens[F[_]](implicit inj: InjectK[RefreshTokenRepository, F]) {
    def store(token: RefreshToken)         = Store(token).into[F]
    def query(email: Email, token: String) = Query(email, token).into[F]
  }

  object RefreshTokens {
    implicit def refreshTokens[F[_]](implicit inj: InjectK[RefreshTokenRepository, F]): RefreshTokens[F] = new RefreshTokens
    def apply[F[_]]()(implicit refreshTokens: RefreshTokens[F])                                          = refreshTokens
  }
}

sealed trait ConfirmationTokenRepository[A]

object ConfirmationTokenRepository {
  case class Store(token: ConfirmationToken)    extends ConfirmationTokenRepository[ConfirmationToken]
  case class Query(email: Email, token: String) extends ConfirmationTokenRepository[Option[ConfirmationToken]]

  def store(token: ConfirmationToken)    = Free.liftF(Store(token))
  def query(email: Email, token: String) = Free.liftF(Query(email, token))

  class ConfirmationTokens[F[_]](implicit inj: InjectK[ConfirmationTokenRepository, F]) {
    def store(token: ConfirmationToken)    = Store(token).into[F]
    def query(email: Email, token: String) = Query(email, token).into[F]
  }

  object ConfirmationTokens {
    implicit def confirmationTokens[F[_]](implicit inj: InjectK[ConfirmationTokenRepository, F]): ConfirmationTokens[F] =
      new ConfirmationTokens
    def apply[F[_]]()(implicit confirmationTokens: ConfirmationTokens[F]) = confirmationTokens
  }
}
