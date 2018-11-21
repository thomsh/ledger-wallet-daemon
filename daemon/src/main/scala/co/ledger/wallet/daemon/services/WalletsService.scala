package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.database._
import co.ledger.wallet.daemon.models.{WalletView, WalletsViewWithCount}
import co.ledger.wallet.daemon.models.Wallet._

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class WalletsService @Inject()(daemonCache: DaemonCache) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  def wallets(user: User, poolName: String, offset: Int, bulkSize: Int): Future[WalletsViewWithCount] = {
    daemonCache.getWallets(offset, bulkSize, poolName, user.pubKey).flatMap { pair =>
      Future.sequence(pair._2.map(_.walletView)).map(WalletsViewWithCount(pair._1, _))
    }
  }

  def wallet(user: User, poolName: String, walletName: String): Future[Option[WalletView]] = {
    daemonCache.getWallet(walletName, poolName, user.pubKey).flatMap {
      case Some(wallet) => wallet.walletView.map(Option(_))
      case None => Future(None)
    }
  }

  def createWallet(user: User, poolName: String, walletName: String, currencyName: String): Future[WalletView] = {
    daemonCache.createWallet(walletName, currencyName, poolName, user).flatMap(_.walletView)
  }

}
