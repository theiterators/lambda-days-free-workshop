package pl.iterators.forum

import org.scalatest.OptionValues
import pl.iterators.forum.domain.{ConfirmationToken, Email, RefreshToken, Token, TokenType}
import pl.iterators.forum.fixtures.Accounts
import pl.iterators.forum.repositories.interpreters._

abstract class TokenRepositoryDbInterpreterSpec[T <: TokenType] extends BaseItSpec with OptionValues {
  def tokenRepository: TokenRepositoryDbInterpreter[T]

  protected def generateToken(email: Email): Token[T]

  it should "store and query tokens" in {
    new Accounts(db)
      .fixture("user@forum.com", "JohnnyBGood", "anything") { id =>
        val token = generateToken(Email("user@forum.com"))
        for {
          _           <- tokenRepository.store(token)
          tokenFromDb <- tokenRepository.query(Email("user@forum.com"), token.value)
        } yield (token, tokenFromDb)

      }
      .map {
        case (generatedToken, maybeTokenFromDb) =>
          generatedToken.value shouldEqual maybeTokenFromDb.value.value
      }

  }
}

class RefreshTokenRepositoryDbInterpreterSpecs extends TokenRepositoryDbInterpreterSpec[TokenType.Refresh] {
  override val tokenRepository                       = new RefreshTokenRepositoryDbInterpreter(db)
  override protected def generateToken(email: Email) = RefreshToken.generate(email)
}

class ConfirmationTokenRepositoryDbInterpreterSpecs extends TokenRepositoryDbInterpreterSpec[TokenType.Confirmation] {
  override val tokenRepository                       = new ConfirmationTokenRepositoryDbInterpreter(db)
  override protected def generateToken(email: Email) = ConfirmationToken.generate(email)
}
