package pl.iterators.forum.utils.directives

import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler

object ExceptionHandlerWithIllegalArgumentException {
  def apply() = ExceptionHandler {
    case (ex: IllegalArgumentException) => complete(BadRequest -> ex.getLocalizedMessage)
  }
}
