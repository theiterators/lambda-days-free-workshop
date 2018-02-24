package pl.iterators.forum.resources

import java.time.Duration

import akka.http.scaladsl.model.Uri
import pl.iterators.forum.domain._
import pl.iterators.forum.domain.tags._
import pl.iterators.forum.utils.db.WithId
import pl.iterators.forum.utils.tag._
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.language.existentials
import scala.util.Try

trait CommonJsonProtocol {
  implicit val uriFormat: Format[Uri] = new Format[Uri] {
    override def writes(o: Uri): JsValue = JsString(o.toString)
    override def reads(json: JsValue): JsResult[Uri] = json match {
      case JsString(input) => Try(Uri(input)).fold(_ => JsError("error.uri"), uri => JsSuccess(uri))
      case _               => JsError("error.expected.jstring")
    }
  }

  implicit val emailReads: Reads[String @@ EmailTag] = {
    case JsString(email) if Email.Valid(email) => JsSuccess(email.@@[EmailTag])
    case JsString(_)                           => JsError("error.email")
    case _                                     => JsError("error.expected.jstring")
  }

  implicit val passwordReads: Reads[String @@ PasswordTag] = {
    case JsString(str) if PasswordPlain.Valid(str) => JsSuccess(str.@@[PasswordTag])
    case JsString(_)                               => JsError("error.password")
    case _                                         => JsError("error.expected.jstring")
  }

  implicit val nickReads: Reads[String @@ NickTag] = {
    case JsString(str) =>
      Nick.Valid(str) match {
        case Left(Nick.TooShort)    => JsError("error.minLength")
        case Left(Nick.TooLong)     => JsError("error.maxLength")
        case Left(Nick.FormatError) => JsError("error.nick")
        case Right(_)               => JsSuccess(str.@@[NickTag])
      }
    case _ => JsError("error.expected.jstring")
  }

  implicit val subjectReads: Reads[String @@ SubjectTag] = {
    case JsString(str) =>
      Subject.Valid(str) match {
        case Left(Subject.Empty)   => JsError("error.empty")
        case Left(Subject.TooLong) => JsError("error.maxLength")
        case Right(_)              => JsSuccess(str.@@[SubjectTag])
      }
    case _ => JsError("error.expected.jstring")
  }

  implicit val postContentReads: Reads[String @@ PostContentTag] = {
    case JsString(str) =>
      PostContent.Valid(str) match {
        case Left(PostContent.Empty)   => JsError("error.empty")
        case Left(PostContent.TooLong) => JsError("error.maxLength")
        case Right(_)                  => JsSuccess(str.@@[PostContentTag])
      }
    case _ => JsError("error.expected.jstring")
  }

  implicit val anyIdReads: Reads[Int @@ IdTag[Any]] = Reads.IntReads.map(_.@@[IdTag[Any]])
  implicit def idReads[A]: Reads[Int @@ IdTag[A]]   = anyIdReads.asInstanceOf[Reads[Int @@ IdTag[A]]]

  implicit val errorWrites: OWrites[Error] = (o: Error) => Json.obj("error" -> o.errorCode)

  implicit val claimsFormat: OFormat[Claims] = Json.format[Claims]

  def resourceWrites[A: Writes, B: OWrites](path: A => Uri): OWrites[WithId[A, B]] =
    (
      implicitly[OWrites[B]] and
        (__ \ "id").write[A] and
        (__ \ "uri").write[Uri]
    )(resource => (resource, resource.id, path(resource.id)))

  implicit val tokenWrites: Writes[(Token[_ <: TokenType], Duration)] = (o: (Token[_ <: TokenType], Duration)) => {
    val (token, ttl) = o
    JsObject(Seq("token" -> JsString(token.value), "expiresAt" -> JsString(token.expiresAtInstant(ttl).toString)))
  }

}

object CommonJsonProtocol extends CommonJsonProtocol
