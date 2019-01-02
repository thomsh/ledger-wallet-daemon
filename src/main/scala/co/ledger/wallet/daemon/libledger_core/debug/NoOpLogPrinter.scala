package co.ledger.wallet.daemon.libledger_core.debug

import co.ledger.core.{ExecutionContext, LogPrinter}

class NoOpLogPrinter(ec: ExecutionContext, val isPrintEnabled: Boolean) extends LogPrinter {
  override def printError(message: String): Unit = print("ERROR", message)
  override def printInfo(message: String): Unit = print("INFO", message)
  override def printDebug(message: String): Unit = print("DEBUG", message)
  override def printWarning(message: String): Unit = print("WARN", message)
  override def printApdu(message: String): Unit = print("APDU", message)
  override def printCriticalError(message: String): Unit = print("WTF", message)
  override def getContext: ExecutionContext = ec

  private def print(tag: String, message: String): Unit = if (isPrintEnabled) {
    println(s"From core [$tag]: $message")
  }
}
