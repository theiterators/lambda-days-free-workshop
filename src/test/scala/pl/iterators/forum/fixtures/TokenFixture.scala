package pl.iterators.forum.fixtures

import java.time.Duration

import cats.{Id, ~>}
import pl.iterators.forum.domain._
import pl.iterators.forum.repositories.RefreshTokenRepository
import pl.iterators.forum.utils.db.WithId

import scala.collection.mutable.ArrayBuffer

trait TokenFixture[T <: TokenType] { accounts: AccountFixture =>
  protected def generateToken(email: Email): Token[T]
  def tokenInterpreter: TokenRepositoryInMemInterpreter[T]

  def createToken(email: String) = {
    val token = generateToken(Email(email))
    tokenInterpreter.store(token)
    token.value
  }

  def createExpiredToken(email: String, ttl: Duration) = {
    val token        = generateToken(Email(email))
    val expiredToken = token.copy(issuedAt = token.issuedAt.minus(ttl.plusSeconds(1))).asInstanceOf[Token[T]]
    tokenInterpreter.store(expiredToken)
    token.value
  }
}

trait RefreshTokenFixture extends TokenFixture[TokenType.Refresh] { accounts: AccountFixture =>
  override protected def generateToken(email: Email) = RefreshToken.generate(Email(email))
  override final val tokenInterpreter                = new RefreshTokenRepositoryInMemInterpreter

  val refreshTokenOrAccountInterpreter = accountInterpreter or tokenInterpreter
}

abstract class TokenRepositoryInMemInterpreter[T <: TokenType] {
  private val storage: ArrayBuffer[Token[T]] = ArrayBuffer.empty

  final def store(token: Token[T]) = {
    val i = storage.length
    storage += token
    WithId(i, token)
  }
  final def find(email: Email) = storage.iterator.zipWithIndex.map(ai => WithId(ai._2, ai._1)).filter(_.email == email).toList
  final def query(email: Email, token: String): Option[WithId[Int, Token[T]]] = {
    val i = storage.indexWhere(t => t.email == email && t.value == token)
    if (i == -1) None else Some(WithId(i, storage(i)))
  }
}

class RefreshTokenRepositoryInMemInterpreter
    extends TokenRepositoryInMemInterpreter[TokenType.Refresh]
    with (RefreshTokenRepository ~> Id) {
  import pl.iterators.forum.repositories.RefreshTokenRepository._

  override def apply[A](fa: RefreshTokenRepository[A]) = fa match {
    case Query(email, token) => query(email, token).map(_.model)
    case Store(refreshToken) => store(refreshToken)
  }

}