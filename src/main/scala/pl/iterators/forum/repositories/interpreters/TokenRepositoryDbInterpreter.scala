package pl.iterators.forum.repositories.interpreters

import java.time.OffsetDateTime

import pl.iterators.forum.domain.TokenType.{Confirmation, Refresh}
import pl.iterators.forum.domain.tags.IdTag
import pl.iterators.forum.domain._
import pl.iterators.forum.repositories._
import pl.iterators.forum.utils.db.PostgresDriver.api._
import pl.iterators.forum.utils.db._
import pl.iterators.forum.utils.tag.@@
import slick.dbio.Effect.{Read, Write}

import scala.concurrent.{ExecutionContext, Future}

trait TokenRepositoryDbInterpreterCompanion[T <: TokenType] extends TypeMappers {
  final type TokenId = Long @@ IdTag[Token[T]]

  abstract class TokensTable(tag: slick.lifted.Tag, tableName: String) extends TableWithId[TokenId, Token[T]](tag, tableName) {
    final def email    = column[Email]("email")
    final def token    = column[String]("token")
    final def issuedAt = column[OffsetDateTime]("issued_at")

    override final val model = (email, token, issuedAt) <> ((Token.apply[T] _).tupled, Token.unapply[T])
  }

  type TokenTableDef <: TokensTable

}

abstract class TokenRepositoryDbInterpreter[T <: TokenType](db: Database)(implicit executionContext: ExecutionContext) {
  import TypeMappers.emailColumnType

  val companion: TokenRepositoryDbInterpreterCompanion[T]
  protected val tokens: Repository[Long @@ IdTag[Token[T]], Token[T], companion.TokenTableDef]

  final type TokenId = Long @@ IdTag[Token[T]]

  final def store(token: Token[T]): DBIOAction[Token[T], NoStream, Write] = tokens.insert(token).map(_ => token)
  final def query(email: Email, token: String): DBIOAction[Option[Token[T]], NoStream, Read] =
    filterEmailAndTokenC((email, token)).result.headOption.map(_.map(_.model))

  private def filterEmailAndToken(email: Rep[Email], token: Rep[String]) =
    tokens.table.filter(row => row.email === email && row.token === token)
  private val filterEmailAndTokenC = Compiled(filterEmailAndToken _)

}

class RefreshTokenRepositoryDbInterpreter(db: Database)(implicit executionContext: ExecutionContext)
    extends TokenRepositoryDbInterpreter[Refresh](db)
    with RefreshTokenRepositoryInterpreter {
  import RefreshTokenRepository._
  import RefreshTokenRepositoryDbInterpreter._

  override def apply[A](fa: RefreshTokenRepository[A]): Future[A] = fa match {
    case Store(refreshToken) => db.run(store(refreshToken))
    case Query(email, token) => db.run(query(email, token))
  }

  override val companion = RefreshTokenRepositoryDbInterpreter
  override protected val tokens =
    new Repository[Long @@ IdTag[RefreshToken], RefreshToken, RefreshTokensTable](TableQuery[RefreshTokensTable])
}

object RefreshTokenRepositoryDbInterpreter extends TokenRepositoryDbInterpreterCompanion[Refresh] {
  final class RefreshTokensTable(tag: slick.lifted.Tag) extends TokensTable(tag, "refresh_tokens")
  override type TokenTableDef = RefreshTokensTable
}

class ConfirmationTokenRepositoryDbInterpreter(db: Database)(implicit executionContext: ExecutionContext)
    extends TokenRepositoryDbInterpreter[Confirmation](db)
    with ConfirmationTokenRepositoryInterpreter {
  import ConfirmationTokenRepository._
  import ConfirmationTokenRepositoryDbInterpreter._

  override def apply[A](fa: ConfirmationTokenRepository[A]): Future[A] = fa match {
    case Store(refreshToken) => db.run(store(refreshToken))
    case Query(email, token) => db.run(query(email, token))
  }

  override val companion = ConfirmationTokenRepositoryDbInterpreter
  override protected val tokens =
    new Repository[Long @@ IdTag[ConfirmationToken], ConfirmationToken, ConfirmationTokensTable](TableQuery[ConfirmationTokensTable])
}

object ConfirmationTokenRepositoryDbInterpreter extends TokenRepositoryDbInterpreterCompanion[Confirmation] {
  final class ConfirmationTokensTable(tag: slick.lifted.Tag) extends TokensTable(tag, "confirmation_tokens")
  override type TokenTableDef = ConfirmationTokensTable
}
