package pl.iterators.forum.utils.directives

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server.{RejectionHandler, ValidationRejection}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport.PlayJsonError

object PlayJsonSupportRejectionHandler {
  import akka.http.scaladsl.server.Directives.complete

  def apply(): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case ValidationRejection(msg, Some(PlayJsonError(_))) =>
          implicit val jsonMarshaller = Marshaller.stringMarshaller(`application/json`)
          complete(BadRequest -> msg)
      }
      .result()
}
