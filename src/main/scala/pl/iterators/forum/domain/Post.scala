package pl.iterators.forum.domain

import java.time.OffsetDateTime

trait Post[+AuthorRep, +ThreadRep] {
  def author: AuthorRep
  def thread: ThreadRep
  def createdAt: OffsetDateTime
  def updatedAt: Option[OffsetDateTime]
  def content: PostContent
}
