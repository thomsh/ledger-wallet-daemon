package co.ledger.wallet.daemon.schedulers.observers

import co.ledger.core._
import com.twitter.inject.Logging

class NewBlockEventReceiver(wallet: Wallet) extends EventReceiver with Logging {
  private val self = this

  override def onEvent(event: Event): Unit = Unit

  private def canEqual(a: Any): Boolean = a.isInstanceOf[NewBlockEventReceiver]

  override def equals(that: Any): Boolean = that match {
    case that: NewBlockEventReceiver => that.canEqual(self) && self.hashCode() == that.hashCode()
    case _ => false
  }

  override def hashCode(): Int = wallet.hashCode

  override def toString: String = s"NewBlockEventReceiver(wallet: $wallet)"

}
