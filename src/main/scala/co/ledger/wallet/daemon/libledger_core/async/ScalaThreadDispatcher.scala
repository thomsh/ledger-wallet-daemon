package co.ledger.wallet.daemon.libledger_core.async

import co.ledger.core.{Lock, ThreadDispatcher}

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class ScalaThreadDispatcher(mainContext: ExecutionContext) extends ThreadDispatcher {
  private val _mainContext = LedgerCoreExecutionContext(mainContext)
  private val _pools = mutable.Map[String, co.ledger.core.ExecutionContext]()
  private val _queues = mutable.Map[String, co.ledger.core.ExecutionContext]()

  override def getSerialExecutionContext(name: String): co.ledger.core.ExecutionContext = synchronized {
    _pools.getOrElseUpdate(name, LedgerCoreExecutionContext.newSerialQueue())
  }
  override def getThreadPoolExecutionContext(name: String): co.ledger.core.ExecutionContext = synchronized {
    _pools.getOrElseUpdate(name, LedgerCoreExecutionContext.newThreadPool())
  }
  override def getMainExecutionContext: co.ledger.core.ExecutionContext = _mainContext
  override def newLock(): Lock = ???
}
