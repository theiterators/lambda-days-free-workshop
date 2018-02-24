package pl.iterators.forum.services

import java.time.Duration

import cats.data.OptionT
import pl.iterators.forum.domain._
import pl.iterators.forum.repositories._
import pl.iterators.forum.services.AuthenticationService.RefreshTokenRequest
import pl.iterators.forum.utils.free.syntax._

trait AuthenticationService {
  import AuthenticationService.AuthRequest

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

  final def obtainRefreshToken(email: Email): RefreshTokenWithAccountOperation[Option[(RefreshToken, Duration)]] = {
    import RefreshTokenOrAccount._

    OptionT(queryAccount(email))
      .filter(_.canLogin)
      .map(_ => RefreshToken.generate(email))
      .semiflatMap(storeToken(_).map((_, refreshTokenTtl)))
      .value
  }

  final def refreshClaims(refreshTokenRequest: RefreshTokenRequest): RefreshTokenWithAccountOperation[RefreshResponse] = {
    import RefreshTokenOrAccount._

    queryToken(refreshTokenRequest.email, refreshTokenRequest.refreshToken)
      .toEither(ifNone = InvalidToken)
      .subflatMap(refreshToken => Either.cond(test = !refreshToken.isExpired(refreshTokenTtl), refreshToken, TokenExpired))
      .flatMap(_ => queryConfirmedAccount(refreshTokenRequest.email).toEither[TokenError](ifNone = InvalidToken))
      .subflatMap(account => Either.cond(test = account.canLogin, account.claims, InvalidToken))
      .value
  }

}

object AuthenticationService {
  case class AuthRequest(email: Email, password: PasswordPlain)
  case class RefreshTokenRequest(email: Email, refreshToken: String)
}
