package co.ledger.wallet.daemon.models

import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConverters._
import scala.collection._

trait GenCache {

  type Cache[K, V] = concurrent.Map[K, V]

  def newCache[K, V](initialCapacity: Int): Cache[K, V] = new ConcurrentHashMap[K, V](initialCapacity).asScala

  val INITIAL_ACCOUNT_CAP_PER_WALLET: Int = 1000
  val INITIAL_POOL_CAP_PER_USER: Int = 50
  val INITIAL_OPERATION_CAP: Int = 1000
  val INITIAL_WALLET_CAP_PER_POOL: Int = 100
}
