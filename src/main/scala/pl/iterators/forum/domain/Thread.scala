package pl.iterators.forum.domain

import java.time.OffsetDateTime

import java.time.format.DateTimeFormatter

import scala.util.Try
trait Thread[+AuthorRep] {
  def author: AuthorRep
  def subject: Subject
  def createdAt: OffsetDateTime
  def numberOfPosts: Int
  def lastPostDate: OffsetDateTime
  def isClosed: Boolean
}

case class ThreadMarker(date: OffsetDateTime, id: ThreadId) {
  def stringRep: String = s"$id;${date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}"
}
object ThreadMarker {
  def fromThreadWithId(thread: ThreadWithId[_]) = ThreadMarker(thread.createdAt, thread.id)
  def parse(s: String): Try[ThreadMarker] = Try {
    val indexOfSemicolon = s.indexOf(';')
    val id               = ThreadId(s.substring(0, indexOfSemicolon).toLong)
    val date             = OffsetDateTime.parse(s.substring(indexOfSemicolon + 1), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    ThreadMarker(date, id)
  }
}
