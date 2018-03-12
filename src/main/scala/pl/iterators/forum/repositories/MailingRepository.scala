package pl.iterators.forum.repositories

import cats.InjectK
import cats.free.{Free, FreeApplicative}
import pl.iterators.forum.domain.{EmailMessage, Ok}
import pl.iterators.forum.utils.free.par.syntax._
import pl.iterators.forum.utils.free.syntax._

import scala.language.higherKinds

sealed trait MailingRepository[A]

object MailingRepository {
  case class SendEmail(message: EmailMessage) extends MailingRepository[Ok.type]

  def sendEmail(message: EmailMessage) = Free.liftF(SendEmail(message))

  class Mailing[F[_]](implicit inj: InjectK[MailingRepository, F]) {
    def sendEmail(message: EmailMessage) = SendEmail(message).into[F]

    class Par {
      def sendEmail(message: EmailMessage): FreeApplicative[F, Ok.type] = SendEmail(message).liftPar[F]
    }
    def par = new Par()
  }
  object Mailing {
    implicit def mailing[F[_]](implicit inj: InjectK[MailingRepository, F]): Mailing[F] = new Mailing
    def apply[F[_]]()(implicit mailing: Mailing[F])                                     = mailing
  }

}
