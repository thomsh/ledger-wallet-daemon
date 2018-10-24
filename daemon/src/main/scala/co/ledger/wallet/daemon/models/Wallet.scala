package co.ledger.wallet.daemon.models

import java.util.concurrent.atomic.AtomicLong

import co.ledger.core
import co.ledger.core.implicits
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.exceptions.InvalidArgumentException
import co.ledger.wallet.daemon.models.Account.{Account, Derivation, ExtendedDerivation}
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.{AsArrayList, HexUtils}
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.inject.Logging

import scala.collection.JavaConverters._
import scala.collection._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class Wallet(private val coreW: core.Wallet, private val pool: Pool) extends Logging with GenCache {
  private[this] val self = this

  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  implicit def asArrayList[T](input: Seq[T]): AsArrayList[T] = new AsArrayList[T](input)
  private val configuration: Map[String, Any] = Map[String, Any]()
  private[this] val currentBlockHeight: AtomicLong = new AtomicLong(-1)

  val name: String = coreW.getName
  val currency: Currency = Currency.newInstance(coreW.getCurrency)

  def lastBlockHeight: Future[Long] = {
      coreW.getLastBlock()
        .map { lastBlock =>
          updateBlockHeight(lastBlock.getHeight)
          currentBlockHeight.get()
        } recover {
        case _: BlockNotFoundException =>
          updateBlockHeight(0)
          currentBlockHeight.get()
      }
    }

  def updateBlockHeight(newHeight: Long): Unit = {
    val updated = currentBlockHeight.updateAndGet(n => Math.max(n, newHeight))
    debug(LogMsgMaker.newInstance("Update block height").append("to", updated).append("at", newHeight).append("wallet", name).toString())
  }

  def walletView: Future[WalletView] = {
    for {
      balance <- getBalance
      ac <- coreW.getAccountCount()
    } yield WalletView(name, ac, balance, currency.currencyView, configuration)
  }

  def accountDerivationPathInfo(index: Int): Future[String] = {
    coreW.getAccount(index).flatMap(_.getFreshPublicAddresses()).map(_.get(0).getDerivationPath)
  }

  def account(index: Int): Future[Option[Account]] = {
    coreW.getAccount(index).map { coreA =>
      Some(Account.newInstance(coreA, self))
    }.recover {
      case _: implicits.AccountNotFoundException => Option.empty[Account]
    }
  }

  def accountCreationInfo(index: Option[Int]): Future[Derivation] = {
    (index match {
      case Some(i) => coreW.getAccountCreationInfo(i)
      case None => coreW.getNextAccountCreationInfo()
    }).map { info => Account.newDerivation(info) }
  }

  def accountExtendedCreation(index: Option[Int]): Future[ExtendedDerivation] =
    index
      .map(coreW.getExtendedKeyAccountCreationInfo(_))
      .getOrElse(coreW.getNextExtendedKeyAccountCreationInfo())
      .map(new ExtendedDerivation(_))

  def accounts(): Future[Seq[Account]] = {
    coreW.getAccountCount().flatMap { count =>
      coreW.getAccounts(0, count).map { coreAs =>
        coreAs.asScala.map { coreA =>
          println(coreA.getIndex)
          val account = Account.newInstance(coreA, self)
          account.startRealTimeObserver()
          account
        }.toList
      }
    }
  }

  def addAccountIfNotExist(derivations: AccountExtendedDerivationView): Future[Account] = {
    val info = new core.ExtendedKeyAccountCreationInfo(
      derivations.accountIndex,
      derivations.derivations.map(_.owner).asArrayList,
      derivations.derivations.map(_.path).asArrayList,
      derivations.derivations.map(_.extKey.get).asArrayList
    )
    accountCreationEpilogue(coreW.newAccountWithExtendedKeyInfo(info), derivations.accountIndex)
  }

  def addAccountIfNotExit(accountDerivations: AccountDerivationView): Future[Account] = {
    val accountCreationInfo = new core.AccountCreationInfo(
      accountDerivations.accountIndex,
      (for (derivationResult <- accountDerivations.derivations) yield derivationResult.owner).asArrayList,
      (for (derivationResult <- accountDerivations.derivations) yield derivationResult.path).asArrayList,
      (for (derivationResult <- accountDerivations.derivations) yield HexUtils.valueOf(derivationResult.pubKey.get)).asArrayList,
      (for (derivationResult <- accountDerivations.derivations) yield HexUtils.valueOf(derivationResult.chainCode.get)).asArrayList
    )
    accountCreationInfo.getOwners.asScala.foreach(o => println(s"Owner: $o"))
    accountCreationEpilogue(coreW.newAccountWithInfo(accountCreationInfo), accountDerivations.accountIndex)
  }

  private def accountCreationEpilogue(coreAccount: Future[core.Account], accountIndex: Int): Future[Account] = {
    coreAccount.map { coreA =>
      info(LogMsgMaker.newInstance("Account created").append("index", coreA.getIndex).append("wallet_name", name).toString())
      Account.newInstance(startListen(coreA), self)
    }.recoverWith {
      case e: implicits.InvalidArgumentException =>
        Future.failed(InvalidArgumentException(e.getMessage))
      case _: implicits.AccountAlreadyExistsException =>
        for {
          _ <- Future(warn(LogMsgMaker.newInstance("Account already exist").append("index", accountIndex).append("wallet_name", name).toString()))
          a <- coreW.getAccount(accountIndex).map { coreA =>
            startListen(coreA)
            Account.newInstance(coreA, self)
          }
        } yield a
    }
  }

  def syncAccounts(poolName: String): Future[Seq[SynchronizationResult]] = {
    accounts().flatMap { accounts =>
      Future.sequence(accounts.map { account => account.sync(poolName)})
    }
  }

  def startCacheAndRealTimeObserver(): Future[Unit] = startListen

  def stopRealTimeObserver(): Unit = {
    accounts().map { as =>
      debug(LogMsgMaker.newInstance("Stop real time observer").append("wallet", self).append("account_count", as.size).toString())
      as.map { a => a.stopRealTimeObserver() }
    }
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Wallet => that.isInstanceOf[Wallet] && self.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    self.name.hashCode + self.currency.hashCode()
  }

  override def toString: String = s"Wallet(name: $name, currency: ${currency.name})"

  private def startListen(): Future[Unit] = {
    coreW.getAccountCount().flatMap { count =>
      coreW.getAccounts(0, count).map { coreAs =>
          coreAs.asScala.foreach { coreA => startListen(coreA) }
        }
      }
    }

  private def startListen(coreA: core.Account): core.Account = {
    if (DaemonConfiguration.realTimeObserverOn && !coreA.isObservingBlockchain) {
      coreA.startBlockchainObservation()
      debug(LogMsgMaker.newInstance(s"Real time observer on ${coreA.isObservingBlockchain}").append("account", coreA.getIndex).toString())
    }
    coreA
  }

  private def getBalance: Future[Long] = {
    accounts().flatMap { as =>
      Future.sequence( for (a <- as) yield a.balance ).map { b => b.sum }
    }
  }

}

object Wallet {
  def newInstance(coreW: core.Wallet, pool: Pool): Wallet = {
    new Wallet(coreW, pool)
  }
}

case class WalletView(
                       @JsonProperty("name") name: String,
                       @JsonProperty("account_count") accountCount: Int,
                       @JsonProperty("balance") balance: Long,
                       @JsonProperty("currency") currency: CurrencyView,
                       @JsonProperty("configuration") configuration: Map[String, Any]
                     )

case class WalletsViewWithCount(count: Int, wallets: Seq[WalletView])
