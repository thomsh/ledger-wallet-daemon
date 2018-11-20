package co.ledger.wallet.daemon.models

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.database.PoolDto
import djinni.NativeLibLoader
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class PoolTest extends AssertionsForJUnit {

  NativeLibLoader.loadLibs()
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  private val testPool = Pool.newInstance(Await.result(Pool.newCoreInstance(new PoolDto("test_pool", 1L, "", Option(0L))), Duration.Inf), 1L)
  private val notExistingWallet = Await.result(testPool.wallet("not_exist"), Duration.Inf)
  private val samePool = Pool.newInstance(Await.result(Pool.newCoreInstance(new PoolDto("test_pool", 1L, "", Option(0L))), Duration.Inf), 1L)

  private val wallet = Await.result(testPool.addWalletIfNotExit("test_wallet", "bitcoin").flatMap { testWallet =>
    testPool.wallet("test_wallet").flatMap { sameWallet =>
      assert(Option((testWallet.name, testWallet.currency.getName)) === sameWallet.map(wallet => (wallet.name, wallet.currency.getName)))
      assert(WalletPoolView("test_pool", 1) === Await.result(testPool.view, Duration.Inf))
      testPool.wallets(0, Int.MaxValue).map { wallets =>
        assert(wallets._1 === 1)
        assert((testWallet.name, testWallet.currency.getName) === wallets._2.map (w => (w.name, w.currency.getName)).head)
        testWallet
      }
    }
  }, Duration.Inf)

  @Test def verifyWalletInPool(): Unit = {
    assert(Option((wallet.name, wallet.currency.getName)) === Await.result(samePool.wallet("test_wallet"), Duration.Inf).map(w => (w.name, w.currency.getName)))
    assert(testPool.name === "test_pool")
    assert(WalletPoolView("test_pool", 1) === Await.result(samePool.view, Duration.Inf))
    assert(notExistingWallet.isEmpty)
    assert(wallet != "wallet")
    assert(testPool != wallet)
  }

  @Test def verifyWalletsInPool(): Unit = {
    val (count, wallets) = Await.result(testPool.wallets(0, 100), Duration.Inf)
    assert(count == 1)
    assert(wallets.size == 1)
    assert(List((wallet.name, wallet.currency.getName)) == wallets.map( w => (w.name, w.currency.getName)))
    assert(Await.result(testPool.sync(), Duration.Inf).isEmpty)
  }

}
