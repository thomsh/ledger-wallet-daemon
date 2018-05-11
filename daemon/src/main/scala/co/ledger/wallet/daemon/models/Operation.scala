package co.ledger.wallet.daemon.models

import java.util.{Date, UUID}

import co.ledger.core
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.models.Account.Account
import co.ledger.wallet.daemon.models.coins.Bitcoin
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonInclude, JsonProperty}
import com.twitter.inject.Logging

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class Operation(private val coreO: core.Operation, private val account: Account, private val wallet: Wallet) extends Logging {
  private[this] val self = this
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  val currencyFamily: CurrencyFamily = CurrencyFamily.valueOf(coreO.getWalletType.name())
  val uid: String = coreO.getUid
  val time: Date = coreO.getDate
  val opType: OperationType = OperationType.valueOf(coreO.getOperationType.name())
  val amount: Long = coreO.getAmount.toLong
  val senders: Seq[String] = coreO.getSenders.asScala
  val recipients: Seq[String] = coreO.getRecipients.asScala
  val fees: Long = coreO.getFees.toLong
  val blockHeight: Option[Long] = if (coreO.getBlockHeight == null) None else Option(coreO.getBlockHeight)
  lazy val trustView: Option[TrustIndicatorView] = Option(coreO.getTrust).map (trust => newTrustIndicatorView(trust))
  lazy val transactionView: TransactionView = newTransactionView(coreO, currencyFamily)

  def operationView: Future[OperationView] =
    confirmations(wallet).map(OperationView(
      uid,
      wallet.currency.name,
      currencyFamily,
      trustView,
      _,
      time,
      blockHeight,
      opType,
      amount,
      fees,
      wallet.name,
      account.index,
      senders,
      recipients,
      transactionView))

  private def confirmations(wallet: Wallet): Future[Long] =
    wallet.lastBlockHeight.map { lastBlockHeight => blockHeight match {
        case Some(height) => lastBlockHeight - height + 1
        case None => 0L
      }
    }

  private def newTransactionView(operation: core.Operation, currencyFamily: CurrencyFamily): TransactionView = {
    if(operation.isComplete) {
      currencyFamily match {
        case CurrencyFamily.BITCOIN => Bitcoin.newTransactionView(operation.asBitcoinLikeOperation().getTransaction)
        case _ => throw new UnsupportedOperationException
      }
    } else { null }
  }

  private def newTrustIndicatorView(trust: core.TrustIndicator): TrustIndicatorView = {
    TrustIndicatorView(trust.getTrustWeight, TrustLevel.valueOf(trust.getTrustLevel.name()), trust.getConflictingOperationUids.asScala, trust.getOrigin)
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Operation => that.isInstanceOf[Operation] && self.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    self.uid.hashCode
  }

  override def toString: String = s"Operation(uid: $uid)"
}

object Operation {

  def newInstance(coreO: core.Operation, account: Account, wallet: Wallet): Operation = {
    new Operation(coreO, account, wallet)
  }

}

case class OperationView(
                          @JsonProperty("uid") uid: String,
                          @JsonProperty("currency_name") currencyName: String,
                          @JsonProperty("currency_family") currencyFamily: CurrencyFamily,
                          @JsonProperty("trust") trust: Option[TrustIndicatorView],
                          @JsonProperty("confirmations") confirmations: Long,
                          @JsonProperty("time") time: Date,
                          @JsonProperty("block_height") blockHeight: Option[Long],
                          @JsonProperty("type") opType: OperationType,
                          @JsonProperty("amount") amount: Long,
                          @JsonProperty("fees") fees: Long,
                          @JsonProperty("wallet_name") walletName: String,
                          @JsonProperty("account_index") accountIndex: Int,
                          @JsonProperty("senders") senders: Seq[String],
                          @JsonProperty("recipients") recipients: Seq[String],
                          @JsonProperty("transaction") @JsonInclude(Include.NON_NULL) transaction: TransactionView
                        )

case class TrustIndicatorView(
                             @JsonProperty("weight") weight: Int,
                             @JsonProperty("level") level: TrustLevel,
                             @JsonProperty("conflicted_operations") conflictedOps: Seq[String],
                             @JsonProperty("origin") origin: String
                             )

case class PackedOperationsView(
                                 @JsonProperty("previous") previous: Option[UUID],
                                 @JsonProperty("next") next: Option[UUID],
                                 @JsonProperty("operations") operations: Seq[OperationView]
                               )
