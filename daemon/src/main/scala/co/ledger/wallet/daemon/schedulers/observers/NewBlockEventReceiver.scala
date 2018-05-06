package co.ledger.wallet.daemon.schedulers.observers

import co.ledger.core.{Account, Event, EventCode, EventReceiver}
import co.ledger.wallet.daemon.models.Wallet
import com.twitter.inject.Logging

import scala.util.{Failure, Success, Try}

class NewBlockEventReceiver(wallet: Wallet) extends EventReceiver with Logging {
  private val self = this

  override def onEvent(event: Event): Unit =
    if (event.getCode == EventCode.NEW_BLOCK) {
      Try(wallet.updateBlockHeight(event.getPayload.getLong(Account.EV_NEW_BLOCK_HEIGHT))) match {
        case Success(_) => // do nothing
        case Failure(e) => error("Failed to update block height with exception", e)
      }
    }

  private def canEqual(a: Any): Boolean = a.isInstanceOf[NewBlockEventReceiver]

  override def equals(that: Any): Boolean = that match {
    case that: NewBlockEventReceiver => that.canEqual(self) && self.hashCode() == that.hashCode()
    case _ => false
  }

  override def hashCode(): Int = wallet.hashCode

  override def toString: String = s"NewBlockEventReceiver(wallet: $wallet)"

}
