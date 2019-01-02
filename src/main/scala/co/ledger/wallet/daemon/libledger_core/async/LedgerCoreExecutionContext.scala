package co.ledger.wallet.daemon.libledger_core.async

import java.util.{Timer, TimerTask}

import co.ledger.core
import co.ledger.wallet.daemon.async.{MDCPropagatingExecutionContext, SerialExecutionContext}

import scala.concurrent.{ExecutionContext, Future}

class LedgerCoreExecutionContext(ec: ExecutionContext) extends co.ledger.core.ExecutionContext {
  private implicit val context = ec

  override def execute(runnable: core.Runnable): Unit = Future {runnable.run()}

  override def delay(runnable: core.Runnable, millis: Long): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = execute(runnable)
    }, millis)
  }

}

object LedgerCoreExecutionContext {
  def apply(ec: ExecutionContext) = new LedgerCoreExecutionContext(ec)
  def newThreadPool() = apply(MDCPropagatingExecutionContext.Implicits.global)
  def newSerialQueue() = apply(SerialExecutionContext.Implicits.global)
}