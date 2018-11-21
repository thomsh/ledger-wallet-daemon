package co.ledger.wallet.daemon.services

import java.util.UUID

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.Operations.{OperationView, PackedOperationsView}
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.utils.Utils._
import co.ledger.core
import co.ledger.wallet.daemon.exceptions.WalletNotFoundException
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import javax.inject.{Inject, Singleton}
import Currency._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountsService @Inject()(defaultDaemonCache: DaemonCache) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  def accounts(user: User, poolName: String, walletName: String): Future[Seq[AccountView]] = {
    for {
      walletOption <- defaultDaemonCache.getWallet(walletName, poolName, user.pubKey)
      wallet <- walletOption.toFuture(WalletNotFoundException(walletName))
      aws <- defaultDaemonCache.getAccounts(user.pubKey, poolName, walletName).flatMap { accounts =>
        Future.sequence(accounts.map { account => account.accountView(walletName, wallet.getCurrency.currencyView) })
      }
    } yield aws
  }

  def account(accountIndex: Int, user: User, poolName: String, walletName: String): Future[Option[AccountView]] = {
    for {
      walletOption <- defaultDaemonCache.getWallet(walletName, poolName, user.pubKey)
      wallet <- walletOption.toFuture(WalletNotFoundException(walletName))
      accountViewOption <- defaultDaemonCache.getAccount(accountIndex, user.pubKey, poolName, walletName).flatMap {
        case Some(account) => account.accountView(walletName, wallet.getCurrency.currencyView).map(Option(_))
        case None => Future(None)
      }
    } yield accountViewOption
  }

  def synchronizeAccount(accountIndex: Int, user: User, poolName: String, walletName: String): Future[Seq[SynchronizationResult]] = {
    defaultDaemonCache.syncOperations(user.pubKey, poolName, walletName, accountIndex)
  }

  def getAccount(accountIndex: Int, user: User, poolName: String, walletName: String): Future[Option[core.Account]] = {
    defaultDaemonCache.getAccount(accountIndex, user.pubKey, poolName, walletName)
  }

  def accountFreshAddresses(accountIndex: Int, user: User, poolName: String, walletName: String): Future[Seq[FreshAddressView]] = {
    defaultDaemonCache.getFreshAddresses(accountIndex, user, poolName, walletName)
  }

  def accountDerivationPath(accountIndex: Int, user: User, poolName: String, walletName: String): Future[String] = {
    defaultDaemonCache.getDerivationPath(accountIndex, user.pubKey, poolName, walletName)
  }

  def nextAccountCreationInfo(user: User, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountDerivationView] = {
    defaultDaemonCache.getNextAccountCreationInfo(user.pubKey, poolName, walletName, accountIndex).map(_.view)
  }

  def nextExtendedAccountCreationInfo(user: User, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountExtendedDerivationView] = {
    defaultDaemonCache.getNextExtendedAccountCreationInfo(user.pubKey, poolName, walletName, accountIndex).map(_.view)
  }

  def accountOperations(
                         user: User,
                         accountIndex: Int,
                         poolName: String,
                         walletName: String,
                         queryParams: OperationQueryParams): Future[PackedOperationsView] = {
    (queryParams.next, queryParams.previous) match {
      case (Some(n), _) =>
        // next has more priority, using database batch instead queryParams.batch
        info(LogMsgMaker.newInstance("Retrieve next batch operation").toString())
        defaultDaemonCache.getNextBatchAccountOperations(user, accountIndex, poolName, walletName, n, queryParams.fullOp)
      case (_, Some(p)) =>
        info(LogMsgMaker.newInstance("Retrieve previous operations").toString())
        defaultDaemonCache.getPreviousBatchAccountOperations(user, accountIndex, poolName, walletName, p, queryParams.fullOp)
      case _ =>
        // new request
        info(LogMsgMaker.newInstance("Retrieve latest operations").toString())
        defaultDaemonCache.getAccountOperations(user, accountIndex, poolName, walletName, queryParams.batch, queryParams.fullOp)
    }
  }

  def firstOperation(user: User, accountIndex: Int, poolName: String, walletName: String): Future[Option[OperationView]] = {
    for {
      (_, wallet, account) <- defaultDaemonCache.getHardAccount(user, poolName, walletName, accountIndex)
      optView <- account.firstOperation flatMap {
        case None => Future(None)
        case Some(o) => Operations.getView(o, wallet, account).map(Some(_))
      }
    } yield optView

  }

  def accountOperation(user: User, uid: String, accountIndex: Int, poolName: String, walletName: String, fullOp: Int): Future[Option[OperationView]] = {
    defaultDaemonCache.getAccountOperation(user, uid, accountIndex, poolName, walletName, fullOp)
  }

  def createAccount(accountCreationBody: AccountDerivationView, user: User, poolName: String, walletName: String): Future[AccountView] = {
    for {
      walletOption <- defaultDaemonCache.getWallet(walletName, poolName, user.pubKey)
      wallet <- walletOption.toFuture(WalletNotFoundException(walletName))
      aw <- defaultDaemonCache.createAccount(accountCreationBody, user, poolName, walletName).flatMap(_.accountView(walletName, wallet.getCurrency.currencyView))
    } yield aw
  }

  def createAccountWithExtendedInfo(derivations: AccountExtendedDerivationView, user: User, poolName: String, walletName: String): Future[AccountView] = {
    for {
      walletOption <- defaultDaemonCache.getWallet(walletName, poolName, user.pubKey)
      wallet <- walletOption.toFuture(WalletNotFoundException(walletName))
      aw <- defaultDaemonCache.createAccount(derivations, user, poolName, walletName).flatMap(_.accountView(walletName, wallet.getCurrency.currencyView))
    } yield aw
  }

}

case class OperationQueryParams(previous: Option[UUID], next: Option[UUID], batch: Int, fullOp: Int)