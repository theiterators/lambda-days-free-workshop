package pl.iterators.forum.domain

object tags {
  trait PasswordTag
  trait EmailTag
  trait NickTag
  trait PostContentTag
  trait SubjectTag

  trait IdTag[+A]
}
