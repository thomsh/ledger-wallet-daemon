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

  def currency(currencyName: String, poolName: String, pubKey: String): Future[Option[CurrencyView]] = {
    daemonCache.getCurrency(currencyName, poolName, pubKey).map { currency => currency.map(_.currencyView) }
  }

  def currencies(poolName: String, pubKey: String): Future[Seq[CurrencyView]] = {
    daemonCache.getCurrencies(poolName, pubKey).map { modelCs => modelCs.map(_.currencyView) }
  }

  def validateAddress(address: String, currencyName: String, poolName: String, pubKey: String): Future[Boolean] = {
    daemonCache.getCurrency(currencyName, poolName, pubKey).flatMap {
      case Some(currency) => Future(currency.validateAddress(address))
      case None => Future.failed(CurrencyNotFoundException(currencyName))
    }
  }
}
