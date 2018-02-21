package pl.iterators.forum.domain

import java.time.{Duration, OffsetDateTime}

import pl.iterators.forum.utils.crypto.Crypto

sealed abstract class TokenType

object TokenType {
  final class Refresh      extends TokenType
  final class Confirmation extends TokenType
}

case class Token[T <: TokenType](email: Email, value: String, issuedAt: OffsetDateTime) {
  def isExpired(ttl: Duration)        = expiresAt(ttl).isBefore(OffsetDateTime.now())
  def expiresAt(ttl: Duration)        = issuedAt.plus(ttl)
  def expiresAtInstant(ttl: Duration) = expiresAt(ttl).toInstant
}

object Token {
  def generate[T <: TokenType](email: Email, tokenLength: Int): Token[T] = {
    require(tokenLength > 0)

    val value    = Crypto.generateStrongRandomAlphanumeric(tokenLength, tokenLength)
    val issuedAt = OffsetDateTime.now()
    Token[T](email, value, issuedAt)
  }
}
