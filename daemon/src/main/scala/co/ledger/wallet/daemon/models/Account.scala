package co.ledger.wallet.daemon.models

import java.util.{Calendar, Date}

import co.ledger.core
import co.ledger.core.implicits.{UnsupportedOperationException, _}
import co.ledger.core.{BitcoinLikePickingStrategy, OperationOrderKey}
import co.ledger.wallet.daemon.clients.ClientFactory
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.controllers.TransactionsController.TransactionInfo
import co.ledger.wallet.daemon.exceptions.SignatureSizeUnmatchException
import co.ledger.wallet.daemon.libledger_core.async.LedgerCoreExecutionContext
import co.ledger.wallet.daemon.models.Currency._
import co.ledger.wallet.daemon.models.coins.Bitcoin
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView
import co.ledger.wallet.daemon.schedulers.observers.{SynchronizationEventReceiver, SynchronizationResult}
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.primitives.UnsignedInteger
import com.twitter.inject.Logging

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}

object Account extends Logging {

  implicit class RichCoreAccount(val a: core.Account) extends AnyVal {
    def balance(implicit ec: ExecutionContext): Future[Long] = Account.balance(a)

    def balances(start: String, end: String, timePeriod: core.TimePeriod)(implicit ec: ExecutionContext): Future[List[Long]] = Account.balances(start, end, timePeriod, a)

    def firstOperation(implicit ec: ExecutionContext): Future[Option[core.Operation]] = Account.firstOperation(a)

    def operationCounts(implicit ec: ExecutionContext): Future[Map[core.OperationType, Int]] = Account.operationCounts(a)

    def accountView(walletName: String, cv: CurrencyView)(implicit ec: ExecutionContext): Future[AccountView] = Account.accountView(walletName, cv, a)

    def signBTCTransaction(rawTx: Array[Byte], signatures: Seq[(Array[Byte], Array[Byte])], currentHeight: Long, c: core.Currency)(implicit ec: ExecutionContext): Future[String] = Account.signBTCTransaction(rawTx, signatures, currentHeight, a, c)

    def createTransaction(transactionInfo: TransactionInfo, c: core.Currency)(implicit ec: ExecutionContext): Future[TransactionView] = Account.createBTCTransaction(transactionInfo, a, c)

    def operation(uid: String, fullOp: Int)(implicit ec: ExecutionContext): Future[Option[core.Operation]] = Account.operation(uid, fullOp, a)

    def operations(offset: Long, batch: Int, fullOp: Int)(implicit ec: ExecutionContext): Future[Seq[core.Operation]] = Account.operations(offset, batch, fullOp, a)

    def operationsCounts(start: Date, end: Date, timePeriod: core.TimePeriod)(implicit ec: ExecutionContext): Future[List[Map[core.OperationType, Int]]] = Account.operationsCounts(start, end, timePeriod, a)

    def freshAddresses(implicit ec: ExecutionContext): Future[Seq[core.Address]] = Account.freshAddresses(a)

    def sync(poolName: String, walletName: String)(implicit ec: ExecutionContext): Future[SynchronizationResult] = Account.sync(poolName, walletName, a)

    def startRealTimeObserver(): Unit = Account.startRealTimeObserver(a)

    def stopRealTimeObserver(): Unit = Account.stopRealTimeObserver(a)
  }

  def balance(a: core.Account)(implicit ex: ExecutionContext): Future[Long] = a.getBalance().map { b =>
    debug(s"Account ${a.getIndex}, balance: ${b.toLong}")
    b.toLong
  }

  def operationCounts(a: core.Account)(implicit ex: ExecutionContext): Future[Map[core.OperationType, Int]] =
    a.queryOperations().addOrder(OperationOrderKey.DATE, true).partial().execute().map { os =>
      os.asScala.groupBy(o => o.getOperationType).map { case (optType, opts) => (optType, opts.size) }
    }

  def accountView(walletName: String, cv: CurrencyView, a: core.Account)(implicit ex: ExecutionContext): Future[AccountView] =
    for {
      b <- balance(a)
      opsCount <- operationCounts(a)
    } yield AccountView(walletName, a.getIndex, b, opsCount, a.getRestoreKey, cv)

  def signBTCTransaction(rawTx: Array[Byte], signatures: Seq[(Array[Byte], Array[Byte])], currentHeight: Long, a: core.Account, c: core.Currency)(implicit ec: ExecutionContext): Future[String] = {
    for {
      // TODO avoid brute force Either resolve
      txId <- c.parseUnsignedBTCTransaction(rawTx, currentHeight) match {
        case Right(tx) =>
          if (tx.getInputs.size != signatures.size) Future.failed(new SignatureSizeUnmatchException(tx.getInputs.size(), signatures.size))
          else {
            tx.getInputs.asScala.zipWithIndex.foreach { case (input, index) =>
              input.pushToScriptSig(c.concatSig(signatures(index)._1)) // signature
              input.pushToScriptSig(signatures(index)._2) // pubkey
            }
            debug(s"transaction after sign '${HexUtils.valueOf(tx.serialize())}'")
            a.asBitcoinLikeAccount().broadcastTransaction(tx)
          }
        case Left(_) => Future.failed(new UnsupportedOperationException("Account type not supported, can't sign transaction"))
      }
    } yield txId
  }

  def createBTCTransaction(transactionInfo: TransactionInfo, a: core.Account, c: core.Currency)(implicit ec: ExecutionContext): Future[TransactionView] = {
    c.getWalletType match {
      case core.WalletType.BITCOIN =>
        for {
          feesPerByte <- transactionInfo.feeAmount match {
            case Some(amount) => Future.successful(c.convertAmount(amount))
            case None => ClientFactory.apiClient.getFees(c.getName).map(f => c.convertAmount(f.getAmount(transactionInfo.feeMethod.get)))
          }
          tx <- a.asBitcoinLikeAccount().buildTransaction()
            .sendToAddress(c.convertAmount(transactionInfo.amount), transactionInfo.recipient)
            .pickInputs(BitcoinLikePickingStrategy.DEEP_OUTPUTS_FIRST, UnsignedInteger.MAX_VALUE.intValue())
            .setFeesPerByte(feesPerByte)
            .build()
          v <- Bitcoin.newUnsignedTransactionView(tx, feesPerByte.toLong)
        } yield v
      case _ => Future.failed(new UnsupportedOperationException("Account type not supported, can't create transaction"))
    }
  }

  def operation(uid: String, fullOp: Int, a: core.Account)(implicit ec: ExecutionContext): Future[Option[core.Operation]] = {
    val q = a.queryOperations()
    q.filter().opAnd(core.QueryFilter.operationUidEq(uid))
    (if (fullOp > 0) q.complete().execute()
    else q.partial().execute()).map { ops =>
      debug(s"${ops.size()} returned with uid $uid")
      ops.asScala.headOption
    }
  }

  def firstOperation(a: core.Account)(implicit ec: ExecutionContext): Future[Option[core.Operation]] = {
    a.queryOperations().addOrder(OperationOrderKey.DATE, false).limit(1).partial().execute()
      .map { ops => ops.asScala.toList.headOption }
  }

  def operations(offset: Long, batch: Int, fullOp: Int, a: core.Account)(implicit ec: ExecutionContext): Future[Seq[core.Operation]] = {
    (if (fullOp > 0) {
      a.queryOperations().addOrder(OperationOrderKey.DATE, true).offset(offset).limit(batch).complete().execute()
    } else {
      a.queryOperations().addOrder(OperationOrderKey.DATE, true).offset(offset).limit(batch).partial().execute()
    }).map { operations => operations.asScala.toList }
  }

  def balances(start: String, end: String, timePeriod: core.TimePeriod, a: core.Account)(implicit ec: ExecutionContext): Future[List[Long]] = {
    a.getBalanceHistory(start, end, timePeriod).map { balances =>
      balances.asScala.toList.map { ba => ba.toLong }
    }
  }

  // TODO: refactor this part once lib-core provides the feature
  def operationsCounts(start: Date, end: Date, timePeriod: core.TimePeriod, a: core.Account)(implicit ec: ExecutionContext): Future[List[Map[core.OperationType, Int]]] = {
    a.queryOperations().addOrder(OperationOrderKey.DATE, true).partial().execute().map { operations =>
      val ops = operations.asScala.toList.filter(op => op.getDate.compareTo(start) >= 0 && op.getDate.compareTo(end) <= 0)
      filter(start, 1, end, standardTimePeriod(timePeriod), ops, Nil)
    }
  }

  @tailrec
  private def filter(start: Date, i: Int, end: Date, timePeriod: Int, operations: List[core.Operation], preResult: List[Map[core.OperationType, Int]]): List[Map[core.OperationType, Int]] = {
    def searchResult(condition: core.Operation => Boolean): Map[core.OperationType, Int] =
      operations.filter(condition).groupBy(op => op.getOperationType).map { case (optType, opts) => (optType, opts.size) }

    val (begin, next) = {
      val calendar = Calendar.getInstance()
      calendar.setTime(start)
      calendar.add(timePeriod, i - 1)
      val begin = calendar.getTime
      calendar.add(timePeriod, 1)
      (begin, calendar.getTime)
    }
    if (end.after(next)) {
      val result = searchResult(op => op.getDate.compareTo(begin) >= 0 && op.getDate.compareTo(next) < 0)
      filter(start, i + 1, end, timePeriod, operations, preResult ::: List(result))
    } else {
      val result = searchResult(op => op.getDate.compareTo(begin) >= 0 && op.getDate.compareTo(end) <= 0)
      preResult ::: List(result)
    }
  }

  private def standardTimePeriod(timePeriod: core.TimePeriod): Int = timePeriod match {
    case core.TimePeriod.DAY => Calendar.DATE
    case core.TimePeriod.MONTH => Calendar.MONTH
    case core.TimePeriod.WEEK => Calendar.WEEK_OF_MONTH
  }

  def freshAddresses(a: core.Account)(implicit ec: ExecutionContext): Future[Seq[core.Address]] = {
    a.getFreshPublicAddresses().map(_.asScala.toList)
  }

  def sync(poolName: String, walletName: String, a: core.Account)(implicit ec: ExecutionContext): Future[SynchronizationResult] = {
    val promise: Promise[SynchronizationResult] = Promise[SynchronizationResult]()
    val receiver: core.EventReceiver = new SynchronizationEventReceiver(a.getIndex, walletName, poolName, promise)
    a.synchronize().subscribe(LedgerCoreExecutionContext(ec), receiver)
    debug(s"Synchronize $a")
    promise.future
  }

  def startRealTimeObserver(a: core.Account): Unit = {
    if (DaemonConfiguration.realTimeObserverOn && !a.isObservingBlockchain) a.startBlockchainObservation()
    debug(LogMsgMaker.newInstance(s"Set real time observer on ${a.isObservingBlockchain}").append("account", a).toString())
  }

  def stopRealTimeObserver(a: core.Account): Unit = {
    debug(LogMsgMaker.newInstance("Stop real time observer").append("account", a).toString())
    if (a.isObservingBlockchain) a.stopBlockchainObservation()
  }


  class Derivation(private val accountCreationInfo: core.AccountCreationInfo) {
    val index: Int = accountCreationInfo.getIndex

    lazy val view: AccountDerivationView = {
      val paths = accountCreationInfo.getDerivations.asScala
      val owners = accountCreationInfo.getOwners.asScala
      val pubKeys = {
        val pks = accountCreationInfo.getPublicKeys
        if (pks.isEmpty) {
          paths.map { _ => "" }
        }
        else {
          pks.asScala.map(HexUtils.valueOf)
        }
      }
      val chainCodes = {
        val ccs = accountCreationInfo.getChainCodes
        if (ccs.isEmpty) {
          paths.map { _ => "" }
        }
        else {
          ccs.asScala.map(HexUtils.valueOf)
        }
      }
      val derivations = paths.indices.map { i =>
        DerivationView(
          paths(i),
          owners(i),
          pubKeys(i) match {
            case "" => None
            case pubKey => Option(pubKey)
          },
          chainCodes(i) match {
            case "" => None
            case chainCode => Option(chainCode)
          })
      }
      AccountDerivationView(index, derivations)
    }
  }

  class ExtendedDerivation(info: core.ExtendedKeyAccountCreationInfo) {
    val index: Int = info.getIndex
    lazy val view: AccountExtendedDerivationView = {
      val extKeys = info.getExtendedKeys.asScala.map(Option.apply).padTo(info.getDerivations.size(), None)
      val derivations = (info.getDerivations.asScala, info.getOwners.asScala, extKeys).zipped map {
        case (path, owner, key) =>
          ExtendedDerivationView(path, owner, key)
      }
      AccountExtendedDerivationView(index, derivations.toList)
    }
  }

  def newDerivation(coreD: core.AccountCreationInfo): Derivation = {
    new Derivation(coreD)
  }
}

case class AccountView(
                        @JsonProperty("wallet_name") walletName: String,
                        @JsonProperty("index") index: Int,
                        @JsonProperty("balance") balance: Long,
                        @JsonProperty("operation_count") operationCounts: Map[core.OperationType, Int],
                        @JsonProperty("keychain") keychain: String,
                        @JsonProperty("currency") currency: CurrencyView
                      )

case class DerivationView(
                           @JsonProperty("path") path: String,
                           @JsonProperty("owner") owner: String,
                           @JsonProperty("pub_key") pubKey: Option[String],
                           @JsonProperty("chain_code") chainCode: Option[String]
                         ) {
  override def toString: String = s"DerivationView(path: $path, owner: $owner, pub_key: $pubKey, chain_code: $chainCode)"
}

case class AccountDerivationView(
                                  @JsonProperty("account_index") accountIndex: Int,
                                  @JsonProperty("derivations") derivations: Seq[DerivationView]
                                ) {

  override def toString: String = s"AccountDerivationView(account_index: $accountIndex, derivations: $derivations)"
}

case class ExtendedDerivationView(
                                   @JsonProperty("path") path: String,
                                   @JsonProperty("owner") owner: String,
                                   @JsonProperty("extended_key") extKey: Option[String],
                                 )

case class AccountExtendedDerivationView(
                                          @JsonProperty("account_index") accountIndex: Int,
                                          @JsonProperty("derivations") derivations: Seq[ExtendedDerivationView]
                                        )

case class FreshAddressView(
                             @JsonProperty("address") address: String,
                             @JsonProperty("derivation_path") derivation: String
                           )