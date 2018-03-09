package pl.iterators.forum.services

import java.time.Duration

import cats.data.{EitherK, OptionT}
import cats.free.Free
import pl.iterators.forum.domain._
import pl.iterators.forum.repositories._
import pl.iterators.forum.services.AuthenticationService.RefreshTokenRequest
import pl.iterators.forum.utils.free.syntax._

trait AuthenticationService {
  import AuthenticationService.{AuthRequest, AuthModule}

  def refreshTokenTtl: Duration

  type AuthenticationResponse = Either[AuthenticationError with Product with Serializable, Claims]
  type RefreshResponse        = Either[TokenError, Claims]

  def authenticate(authRequest: AuthRequest): AccountOperation[AuthenticationResponse] =
    AccountRepository
      .queryConfirmed(authRequest.email)
      .toEither(ifNone = InvalidCredentials)
      .subflatMap(subject => Either.cond(test = subject.canLogin, subject, if (subject.isBanned) Banned else InvalidCredentials))
      .subflatMap(subject => Either.cond(test = subject.validatePassword(authRequest.password), subject, InvalidCredentials))
      .map(_.claims)
      .value

  final def obtainRefreshToken(email: Email): AuthModule.Operation[Option[(RefreshToken, Duration)]] = {
    import AuthModule._

    OptionT(accounts.queryEmail(email))
      .filter(_.canLogin)
      .map(_ => RefreshToken.generate(email))
      .semiflatMap(refreshTokens.store(_).map((_, refreshTokenTtl)))
      .value
  }

  final def refreshClaims(refreshTokenRequest: RefreshTokenRequest): AuthModule.Operation[Either[TokenError, Claims]] = {
    import AuthModule._

    refreshTokens
      .query(refreshTokenRequest.email, refreshTokenRequest.refreshToken)
      .toEither(ifNone = InvalidToken)
      .subflatMap(refreshToken => Either.cond(test = !refreshToken.isExpired(refreshTokenTtl), refreshToken, TokenExpired))
      .flatMap(_ => accounts.queryConfirmed(refreshTokenRequest.email).toEither[TokenError](ifNone = InvalidToken))
      .subflatMap(account => Either.cond(test = account.canLogin, account.claims, InvalidToken))
      .value
  }

}

object AuthenticationService {
  case class AuthRequest(email: Email, password: PasswordPlain)
  case class RefreshTokenRequest(email: Email, refreshToken: String)

  object AuthModule {
    type Algebra[A]   = EitherK[AccountRepository, RefreshTokenRepository, A]
    type Operation[A] = Free[Algebra, A]

    import AccountRepository.Accounts
    import RefreshTokenRepository.RefreshTokens

    val accounts: Accounts[Algebra]           = Accounts()
    val refreshTokens: RefreshTokens[Algebra] = RefreshTokens()
  }
}
