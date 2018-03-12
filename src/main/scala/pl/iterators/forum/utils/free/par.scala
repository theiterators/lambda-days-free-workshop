package pl.iterators.forum.utils.free

import cats.{Applicative, InjectK, ~>}
import cats.free.{Free, FreeApplicative}

import scala.language.{higherKinds, implicitConversions}

object par {
  type SeqPar[F[_], A] = Free[FreeApplicative[F, ?], A]

  implicit def parInterpreter[F[_], M[_]: Applicative](interpreter: F ~> M): FreeApplicative[F, ?] ~> M =
    λ[FreeApplicative[F, ?] ~> M](_.foldMap(interpreter))

  object syntax {
    implicit def seqparOperationSyntax[F[_], A](fa: F[A]): DSLOps[F, A]             = new DSLOps(fa)
    implicit def freeapSyntax[F[_], A](freeap: FreeApplicative[F, A]): ParOps[F, A] = new ParOps(freeap)
  }

  final class DSLOps[F[_], A](val fa: F[A]) extends AnyVal {
    def liftPar[G[_]](implicit inject: InjectK[F, G]): FreeApplicative[G, A] = FreeApplicative.lift(inject(fa))
  }

  final class ParOps[F[_], A](val freeap: FreeApplicative[F, A]) extends AnyVal {
    import cats.implicits._

    def liftSeq: SeqPar[F, A]                                       = Free.liftF(freeap)
    def >*[B](second: FreeApplicative[F, B]): FreeApplicative[F, B] = (freeap, second).mapN { case (_, b) => b }
  }

}
