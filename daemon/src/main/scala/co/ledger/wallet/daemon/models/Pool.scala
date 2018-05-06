package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.core.implicits.{CurrencyNotFoundException => CoreCurrencyNotFoundException, _}
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.clients.ClientFactory
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.database.PoolDto
import co.ledger.wallet.daemon.exceptions.{CurrencyNotFoundException, WalletNotFoundException}
import co.ledger.wallet.daemon.libledger_core.async.LedgerCoreExecutionContext
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.schedulers.observers.{NewBlockEventReceiver, SynchronizationResult}
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.inject.Logging
import org.bitcoinj.core.Sha256Hash

import scala.collection.JavaConverters._
import scala.collection._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

class Pool(private val coreP: core.WalletPool, val id: Long) extends Logging with GenCache {
  private[this] val self = this

  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  private val _coreExecutionContext = LedgerCoreExecutionContext.newThreadPool()
  private[this] val eventReceivers: mutable.Set[core.EventReceiver] = utils.newConcurrentSet[core.EventReceiver]
  private[this] val cachedWallets: Cache[String, Wallet] = newCache(initialCapacity = INITIAL_WALLET_CAP_PER_POOL)
  private[this] val cachedCurrencies: Cache[String, Currency] = newCache(initialCapacity = INITIAL_CURRENCY_CAP)
  private[this] var orderedNames = new ListBuffer[String]()
  
  val name: String = coreP.getName

  def view: Future[WalletPoolView] = toCacheAndStartListen(orderedNames.length).map { _ =>
    WalletPoolView(name, orderedNames.length)
  }

  /**
    * Obtain wallets by batch size and offset. If the specified offset and batch size are within the existing
    * cached wallets, the cached results will be returned. Otherwise the difference will be retrieved from core.
    *
    * @param offset the offset the query starts from.
    * @param batch the size of query.
    * @return a tuple of total wallet count and a sequence of wallets from offset to batch size.
    */
  def wallets(offset: Int, batch: Int): Future[(Int, Seq[Wallet])] = {
    assert(offset >= 0 && offset <= orderedNames.length, s"offset invalid $offset")
    assert(batch > 0, "batch must be positive")
    if (offset + batch <= orderedNames.length) {
      Future.successful(fromCache(offset, batch)).map { wallets => (orderedNames.length, wallets) }
    } else {
       toCacheAndStartListen(orderedNames.length).map { _ =>
         val endCount = orderedNames.length min (offset + batch)
         (endCount, fromCache(offset, endCount))
       }
    }
  }

  private def fromCache(offset: Int, batch: Int): Seq[Wallet] = {
    (offset until (offset + batch)).map { index =>
      cachedWallets.getOrElse(orderedNames(index), throw WalletNotFoundException(orderedNames(index)))
    }
  }

  /**
    * Obtain wallet by name. If the name doesn't exist in local cache, a core retrieval will be performed.
    *
    * @param walletName name of wallet.
    * @return a future of optional wallet.
    */
  def wallet(walletName: String): Future[Option[Wallet]] = {
    cachedWallets.get(walletName) match {
      case Some(wallet) => Future.successful(Option(wallet))
      case None => toCacheAndStartListen(orderedNames.length).map { _ => cachedWallets.get(walletName)}
    }
  }

  /**
    * Obtain currency by name. If can not find a result from cache, a call to core will be performed.
    *
    * @param currencyName the specified currency name.
    * @return a future of optional currency.
    */
  def currency(currencyName: String): Future[Option[Currency]] = cachedCurrencies.get(currencyName) match {
    case Some(c) => Future.successful(Option(c))
    case None => coreP.getCurrency(currencyName).map { coreC =>
      addToCache(coreC)
      cachedCurrencies.get(currencyName)
    }.recover {
      case _: CoreCurrencyNotFoundException => None
    }
  }

  /**
    * Obtain currencies.
    *
    * @return future of currencies sequence.
    */
  def currencies(): Future[Seq[Currency]] =
    if (cachedCurrencies.isEmpty) { currenciesFromCore.map { _ => cachedCurrencies.values.toSeq }}
    else { Future.successful(cachedCurrencies.values.toSeq) }


  private val currenciesFromCore: Future[Unit] = coreP.getCurrencies().map { coreCs =>
    coreCs.asScala.foreach { coreC =>
      addToCache(coreC)
    }
  }

  private def addToCache(coreC: core.Currency): Unit = {
    cachedCurrencies.put(coreC.getName, Currency.newInstance(coreC))
    debug(s"Add ${cachedCurrencies(coreC.getName)} to $self cache")
  }

  /**
    * Clear the event receivers on this pool and underlying wallets. It will also call `stopRealTimeObserver` method.
    *
    * @return a future of Unit.
    */
  def clear: Future[Unit] = {
    Future.successful(stopRealTimeObserver()).map { _ =>
      unregisterEventReceivers()
    }
  }

  def addWalletIfNotExit(walletName: String, currencyName: String): Future[Wallet] = {
    coreP.getCurrency(currencyName).flatMap { coreC =>
      coreP.createWallet(walletName, coreC, core.DynamicObject.newInstance()).map { _ =>
        info(LogMsgMaker.newInstance("Wallet created").append("name", walletName).append("pool_name", name).append("currency_name", currencyName).toString())
        toCacheAndStartListen(orderedNames.length).map { _ => cachedWallets(walletName) }
      }.recover {
        case _: WalletAlreadyExistsException =>
          warn(LogMsgMaker.newInstance("Wallet already exist")
            .append("name", walletName)
            .append("pool_name", name)
            .append("currency_name", currencyName)
            .toString())
          if (cachedWallets.contains(walletName)) { Future.successful(cachedWallets(walletName)) }
          else { toCacheAndStartListen(orderedNames.length).map { _ => cachedWallets(walletName) }}
      }.flatten
    }.recover {
      case _: CoreCurrencyNotFoundException => throw CurrencyNotFoundException(currencyName)
    }
  }

  /**
    * Subscribe specied event receiver to core pool, also save the event receiver to the local container.
    *
    * @param eventReceiver the event receiver object need to be registered.
    */
  def registerEventReceiver(eventReceiver: core.EventReceiver): Unit = {
    if (! eventReceivers.contains(eventReceiver)) {
      eventReceivers += eventReceiver
      coreP.getEventBus.subscribe(_coreExecutionContext, eventReceiver)
      debug(s"Register $eventReceiver")
    } else {
      debug(s"Already registered $eventReceiver")
    }
  }

  /**
    * Unsubscribe all event receivers for this pool, including empty the event receivers container in memory.
    *
    */
  def unregisterEventReceivers(): Unit = {
    eventReceivers.foreach { eventReceiver =>
      coreP.getEventBus.unsubscribe(eventReceiver)
      eventReceivers.remove(eventReceiver)
      debug(s"Unregister $eventReceiver")
    }
  }

  /**
    * Synchronize all accounts within this pool.
    *
    * @return a future of squence of synchronization results.
    */
  def sync(): Future[Seq[SynchronizationResult]] = {
    toCacheAndStartListen(orderedNames.length).flatMap { _ =>
      Future.sequence(cachedWallets.values.map { wallet => wallet.syncAccounts(name) }.toSeq).map(_.flatten)
    }
  }

  /**
    * Start real time observer of this pool will start the observers of the underlying wallets and accounts.
    *
    * @return a future of Unit.
    */
  def startCacheAndRealTimeObserver(): Future[Unit] = {
    toCacheAndStartListen(orderedNames.length)
  }

  /**
    * Stop real time observer of this pool will stop the observers of the underlying wallets and accounts.
    *
    * @return a Unit.
    */
  def stopRealTimeObserver(): Unit = {
    debug(LogMsgMaker.newInstance("Stop real time observer").append("pool", name).toString())
    cachedWallets.values.foreach { wallet => wallet.stopRealTimeObserver() }
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Pool => that.isInstanceOf[Pool] && self.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    self.id.hashCode() + self.name.hashCode
  }

  override def toString: String = s"Pool(name: $name, id: $id)"

  private def toCacheAndStartListen(offset: Int): Future[Unit] = {
    if (orderedNames.length > offset) { Future { info(s"Pool $name cache was already updated")} }
    else {
      coreP.getWalletCount().flatMap { count =>
        if (count == offset) {
          Future(info(s"Pool $name cache is up to date"))
        } else if (count < offset)  {
          Future(warn(s"Offset should be less than count, possible race condition"))
        } else {
          coreP.getWallets(offset, count).flatMap { coreWs =>
            Future.sequence(coreWs.asScala.map { coreW =>
              val wallet = Wallet.newInstance(coreW, self)
              orderedNames += wallet.name
              cachedWallets.put(wallet.name, wallet)
              debug(s"Add ${cachedWallets(wallet.name)} to $self cache")
              self.registerEventReceiver(new NewBlockEventReceiver(wallet))
              wallet.startCacheAndRealTimeObserver()
            }).map(_ => ())
          }
        }
      }
    }
  }

}

object Pool {
  def newInstance(coreP: core.WalletPool, id: Long): Pool = {
    new Pool(coreP, id)
  }

  def newCoreInstance(poolDto: PoolDto): Future[core.WalletPool] = {
    core.WalletPoolBuilder.createInstance()
      .setHttpClient(ClientFactory.httpClient)
      .setWebsocketClient(ClientFactory.webSocketClient)
      .setLogPrinter(new NoOpLogPrinter(ClientFactory.threadDispatcher.getMainExecutionContext, DaemonConfiguration.isPrintCoreLibLogsEnabled))
      .setThreadDispatcher(ClientFactory.threadDispatcher)
      .setPathResolver(new ScalaPathResolver(corePoolId(poolDto.userId, poolDto.name)))
      .setRandomNumberGenerator(new SecureRandomRNG)
      .setDatabaseBackend(core.DatabaseBackend.getSqlite3Backend)
      .setConfiguration(core.DynamicObject.newInstance())
      .setName(poolDto.name)
      .build()
  }

  private def corePoolId(userId: Long, poolName: String): String = HexUtils.valueOf(Sha256Hash.hash(s"$userId:$poolName".getBytes))
}

case class WalletPoolView(
                           @JsonProperty("name") name: String,
                           @JsonProperty("wallet_count") walletCount: Int
                         )

