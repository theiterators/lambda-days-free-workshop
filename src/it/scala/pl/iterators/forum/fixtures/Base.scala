package pl.iterators.forum.fixtures

import pl.iterators.forum.utils.db.PostgresDriver.api._

import scala.concurrent.{ExecutionContext, Future}

class Base(db: Database) {
  private case class IntentionalRollbackException[R](result: R) extends Exception("Rolling back transaction after test")

  def withRollback[A](testCode: => DBIO[A])(implicit ec: ExecutionContext): Future[A] = {
    val testWithRollback = testCode flatMap (a => DBIO.failed(IntentionalRollbackException(a)))

    val testResult = db.run(testWithRollback.transactionally)

    testResult.recover {
      case IntentionalRollbackException(success) => success.asInstanceOf[A]
    }
  }
}
