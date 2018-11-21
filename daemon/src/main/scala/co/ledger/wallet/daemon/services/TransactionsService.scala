package co.ledger.wallet.daemon.services

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.TransactionsController.{AccountInfo, TransactionInfo}
import co.ledger.wallet.daemon.database.DefaultDaemonCache
import co.ledger.wallet.daemon.exceptions.WalletNotFoundException
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView
import co.ledger.wallet.daemon.utils.Utils._
import javax.inject.{Inject, Singleton}
import co.ledger.wallet.daemon.models.Wallet._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Business logic for transaction operations.
  *
  * User: Ting Tu
  * Date: 24-04-2018
  * Time: 14:14
  *
  */
@Singleton
class TransactionsService @Inject()(defaultDaemonCache: DefaultDaemonCache) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  def createTransaction(transactionInfo: TransactionInfo, accountInfo: AccountInfo): Future[TransactionView] = {
    for {
      walletOption <- defaultDaemonCache.getWallet(accountInfo.walletName, accountInfo.poolName, accountInfo.user.pubKey)
      wallet <- walletOption.toFuture(WalletNotFoundException(accountInfo.walletName))
      tv <- defaultDaemonCache.getHardAccount(accountInfo.user, accountInfo.poolName, accountInfo.walletName, accountInfo.index)
        .flatMap { case (_, _, account) =>
          account.createTransaction(transactionInfo, wallet.getCurrency)
        }
    } yield tv
  }

  def signTransaction(rawTx: Array[Byte], pairedSignatures: Seq[(Array[Byte],Array[Byte])], accountInfo: AccountInfo): Future[String] = {
    for {
      walletOption <- defaultDaemonCache.getWallet(accountInfo.walletName, accountInfo.poolName, accountInfo.user.pubKey)
      wallet <- walletOption.toFuture(WalletNotFoundException(accountInfo.walletName))
      currentHeight <- wallet.lastBlockHeight
      r <- defaultDaemonCache.getHardAccount(accountInfo.user, accountInfo.poolName, accountInfo.walletName, accountInfo.index)
        .flatMap { case (_, _, account) =>
          account.signBTCTransaction(rawTx, pairedSignatures, currentHeight, wallet.getCurrency)
        }
    } yield r
  }
}
