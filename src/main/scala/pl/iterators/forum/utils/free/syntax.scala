package pl.iterators.forum.utils.free

import cats.data._
import cats.free.{Free, FreeApplicative}
import cats.{InjectK, ~>}
import pl.iterators.forum.utils.free.par.SeqPar

import scala.language.{higherKinds, implicitConversions}

object syntax {
  implicit def freeSyntaxOption[S[_], A](f: Free[S, Option[A]]): FreeOptionOps[S, A]                      = new FreeOptionOps(f)
  implicit def freeSyntaxEither[S[_], A, B](f: Free[S, Either[A, B]]): FreeEitherOps[S, A, B]             = new FreeEitherOps(f)
  implicit def freeSyntax[S[_], A](f: Free[S, A]): FreeOps[S, A]                                          = new FreeOps(f)
  implicit def operationSyntax[S[_], A](s: S[A]): DSLOps[S, A]                                            = new DSLOps(s)
  implicit def anyValSyntax[A](a: A): AnyValOps[A]                                                        = new AnyValOps(a)
  implicit def eitherTSyntax[F[_], A, B](eitherT: EitherT[F, A, B]): EitherTOps[F, A, B]                  = new EitherTOps(eitherT)
  implicit def kleisliEitherTSyntax[S[_], Env, A, B](kleisli: Kleisli[EitherT[Free[S, ?], A, ?], Env, B]) = new KleisliEitherTOps(kleisli)
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
  def liftPar: SeqPar[S, A] = {
    val parCompiler = λ[S ~> FreeApplicative[S, ?]](FreeApplicative.lift(_))
    f.compile(parCompiler)
  }
}

final class AnyValOps[A](val a: A) extends AnyVal {
  def pure[S[_]]: Free[S, A] = Free.pure(a)
}

final class EitherTOps[F[_], A, B](val eitherT: EitherT[F, A, B]) extends AnyVal {
  def assume[Env]: Kleisli[EitherT[F, A, ?], Env, B] = Kleisli.liftF(eitherT)
}

final class KleisliEitherTOps[S[_], Env, A, B](val kleisli: Kleisli[EitherT[Free[S, ?], A, ?], Env, B]) extends AnyVal {
  def onSuccessRun[C](f: B => Kleisli[Free[S, ?], Env, C]): Kleisli[Free[S, ?], Env, Either[A, C]] =
    kleisli.flatMap(b => f(b).mapF(fc => EitherT.right[A](fc))).mapF(_.value)
}
