package pl.iterators.forum

import cats.data.EitherK
import cats.free.Free
import pl.iterators.forum.domain.{ConfirmationToken, Email, RefreshToken}
import pl.iterators.forum.utils.free.syntax._

package object repositories {
  type AccountOperation[A]                      = Free[AccountRepository, A]
  type RefreshTokenOrAccount[A]                 = EitherK[AccountRepository, RefreshTokenRepository, A]
  type RefreshTokenWithAccountOperation[A]      = Free[RefreshTokenOrAccount, A]
  type ConfirmationTokenOperation[A]            = Free[ConfirmationTokenRepository, A]
  type ConfirmationTokenOrAccount[A]            = EitherK[AccountRepository, ConfirmationTokenRepository, A]
  type ConfirmationTokenWithAccountOperation[A] = Free[ConfirmationTokenOrAccount, A]

  object RefreshTokenOrAccount {
    def queryAccount(email: Email)              = AccountRepository.QueryEmail(email).into[RefreshTokenOrAccount]
    def queryConfirmedAccount(email: Email)     = AccountRepository.QueryConfirmed(email).into[RefreshTokenOrAccount]
    def storeToken(refreshToken: RefreshToken)  = RefreshTokenRepository.Store(refreshToken).into[RefreshTokenOrAccount]
    def queryToken(email: Email, token: String) = RefreshTokenRepository.Query(email, token).into[RefreshTokenOrAccount]
  }

  object ConfirmationTokenOrAccount {
    def storeToken(confirmationToken: ConfirmationToken) =
      ConfirmationTokenRepository.Store(confirmationToken).into[ConfirmationTokenOrAccount]
  }
}
