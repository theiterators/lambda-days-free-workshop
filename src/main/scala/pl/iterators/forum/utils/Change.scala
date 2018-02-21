package pl.iterators.forum.utils

import play.api.libs.json._

import scala.language.implicitConversions

sealed abstract class Change[+A] {
  def getValue[A1 >: A](oldValue: Option[A1]): Option[A1]
  def toOption: Option[A] = this match {
    case Modify(newValue) => Some(newValue)
    case _                => None
  }
  def fold[B](ifLeave: => B)(f: Option[A] => B): B = this match {
    case Leave => ifLeave
    case _     => f(this.toOption)
  }
}
case object Remove extends Change[Nothing] {
  override def getValue[A1](oldValue: Option[A1]): Option[A1] = None
}
case object Leave extends Change[Nothing] {
  override def getValue[A1](oldValue: Option[A1]): Option[A1] = oldValue
}
case class Modify[+A](newValue: A) extends Change[A] {
  override def getValue[A1 >: A](oldValue: Option[A1]): Option[A1] = Some(newValue)
}

object Change {
  implicit def fromOption[A](opt: Option[A]): Change[A] = opt.fold[Change[A]](Leave)(Modify(_))

  private object EmptyJsValue {
    def unapply(arg: JsValue): Boolean = arg match {
      case (JsNull | JsString("")) => true
      case _                       => false
    }
  }

  implicit class ChangeReads(val path: JsPath) extends AnyVal {
    def readChange[T](implicit reads: Reads[T]): Reads[Change[T]] = Reads[Change[T]] { json =>
      path
        .applyTillLast(json)
        .fold(jserr => jserr,
              jsres =>
                jsres.fold(_ => JsSuccess(Leave), {
                  case EmptyJsValue() => JsSuccess(Remove)
                  case js             => reads.reads(js).repath(path).map(Modify(_))
                }))
    }
  }
}
