package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.models.WalletPoolView
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PoolsService @Inject()(daemonCache: DaemonCache) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import PoolsService._

  def createPool(user: User, poolName: String, configuration: PoolConfiguration): Future[WalletPoolView] = {
    daemonCache.createWalletPool(user, poolName, configuration.toString).flatMap(_.view)
  }

  def pools(user: User): Future[Seq[WalletPoolView]] = {
    daemonCache.getWalletPools(user.pubKey).flatMap { pools => Future.sequence(pools.map(_.view))}
  }

  def pool(user: User, poolName: String): Future[Option[WalletPoolView]] = {
    daemonCache.getWalletPool(user.pubKey, poolName).flatMap {
      case Some(pool) => pool.view.map(Option(_))
      case None => Future(None)
    }
  }

  def syncOperations(user: User, poolName: String): Future[Seq[SynchronizationResult]] = {
    daemonCache.syncOperations(user.pubKey, poolName)
  }

  def syncOperations(): Future[Seq[SynchronizationResult]] = {
    daemonCache.syncOperations()
  }

  def removePool(user: User, poolName: String): Future[Unit] = {
    daemonCache.deleteWalletPool(user, poolName)
  }

}

object PoolsService {

  case class PoolConfiguration() {
    override def toString: String = ""
  }
}