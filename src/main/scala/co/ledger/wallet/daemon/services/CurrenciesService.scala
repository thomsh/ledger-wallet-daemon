package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.exceptions.CurrencyNotFoundException
import co.ledger.wallet.daemon.models._
import Currency._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CurrenciesService @Inject()(daemonCache: DaemonCache) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  def currency(currencyName: String, poolInfo: PoolInfo): Future[Option[CurrencyView]] = {
    daemonCache.getCurrency(currencyName, poolInfo).map { currency => currency.map(_.currencyView) }
  }

  def currencies(poolInfo: PoolInfo): Future[Seq[CurrencyView]] = {
    daemonCache.getCurrencies(poolInfo).map { modelCs => modelCs.map(_.currencyView) }
  }

  def validateAddress(address: String, currencyName: String, poolInfo: PoolInfo): Future[Boolean] = {
    daemonCache.getCurrency(currencyName, poolInfo).flatMap {
      case Some(currency) => Future(currency.validateAddress(address))
      case None => Future.failed(CurrencyNotFoundException(currencyName))
    }
  }
}
