package co.ledger.wallet.daemon.async

import scala.concurrent.{ExecutionContext, Future}

object SerialExecutionContext {
  object Implicits{
    implicit lazy val global: ExecutionContext = SerialExecutionContextWrapper(ExecutionContext.Implicits.global)
  }

}

class SerialExecutionContextWrapper(implicit val ec: ExecutionContext) extends ExecutionContext with MDCPropagatingExecutionContext {
  private var _lastTask: Future[Unit] = Future.unit

  override def execute(runnable: Runnable): Unit = synchronized {
    _lastTask = _lastTask.map(_ => runnable.run())
  }

  override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)
}

object SerialExecutionContextWrapper {
  def apply(implicit wrapped: ExecutionContext): SerialExecutionContextWrapper = {
    new SerialExecutionContextWrapper()
  }
}