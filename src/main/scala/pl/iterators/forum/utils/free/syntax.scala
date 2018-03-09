package pl.iterators.forum.utils.free

import cats.data._
import cats.free.Free
import cats.syntax.either._
import cats.{InjectK, ~>}

import scala.language.{higherKinds, implicitConversions}

object syntax {
  implicit def freeSyntaxOption[S[_], A](f: Free[S, Option[A]]): FreeOptionOps[S, A]          = new FreeOptionOps(f)
  implicit def freeSyntaxEither[S[_], A, B](f: Free[S, Either[A, B]]): FreeEitherOps[S, A, B] = new FreeEitherOps(f)
  implicit def freeSyntax[S[_], A](f: Free[S, A]): FreeOps[S, A]                              = new FreeOps(f)
  implicit def operationSyntax[S[_], A](s: S[A]): DSLOps[S, A]                                = new DSLOps(s)
  implicit def anyValSyntax[A](a: A): AnyValOps[A]                                            = new AnyValOps(a)
}

final class FreeOptionOps[S[_], A](val f: Free[S, Option[A]]) extends AnyVal {
  def toEither[B](ifNone: => B): EitherT[Free[S, ?], B, A] = EitherT.fromOptionF(f, ifNone)
}

final class FreeEitherOps[S[_], A, B](val f: Free[S, Either[A, B]]) extends AnyVal {
  def assumeT[Env]: ReaderT[EitherT[Free[S, ?], A, ?], Env, B] = ReaderT.liftF(EitherT(f))
}

final class DSLOps[S[_], A](val s: S[A]) extends AnyVal {
  def into[T[_]](implicit inject: InjectK[S, T]): Free[T, A] = Free.inject[S, T](s)
}

final class FreeOps[S[_], A](val f: Free[S, A]) extends AnyVal {
  def as[T[_]](implicit inject: InjectK[S, T]): Free[T, A] =
    f.compile(new (S ~> T) {
      override def apply[B](fa: S[B]): T[B] = inject.inj(fa)
    })
  def assume[Env]: ReaderT[Free[S, ?], Env, A] = ReaderT.liftF(f)
  def toEitherT[B]: EitherT[Free[S, ?], B, A]  = EitherT.liftF(f)
}

final class AnyValOps[A](val a: A) extends AnyVal {
  def pure[S[_]]: Free[S, A] = Free.pure(a)
}
