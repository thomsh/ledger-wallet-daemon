package co.ledger.wallet.daemon.modules

import java.util.concurrent.TimeUnit

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import javax.inject.Singleton
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.database.{DaemonCache, DefaultDaemonCache}
import co.ledger.wallet.daemon.services.{PoolsService, UsersService}
import com.google.inject.Provides
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.inject.{Injector, TwitterModule}
import com.twitter.util.{Duration, ScheduledThreadPoolTimer, Time}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object DaemonCacheModule extends TwitterModule {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  @Singleton
  @Provides
  def provideDaemonCache: DaemonCache = {
    val cache = new DefaultDaemonCache()
    val t0 = System.currentTimeMillis()
    Await.result(cache.dbMigration, 1.minutes)
    info(s"Database migration end, elapsed time: ${System.currentTimeMillis() - t0} milliseconds")
    cache
  }

  override def singletonPostWarmupComplete(injector: Injector): Unit = {
    val poolsService = injector.instance[PoolsService](classOf[PoolsService])

    def synchronizationTask(): Unit = {
      val t0 = System.currentTimeMillis()
      Try(Await.result(poolsService.syncOperations, 5.minutes)) match {
        case Success(result) =>
          result.foreach { r =>
            if (r.syncResult) { info(s"Synchronization complete for $r") }
            else { warn(s"Failed synchronizing $r") }
          }
          val t1 = System.currentTimeMillis()
          info(s"Synchronization finished, elapsed time: ${t1 - t0} milliseconds")
        case Failure(e) =>
          error("Synchronization failed with exception", e)
      }
    }

    val usersService = injector.instance[UsersService](classOf[UsersService])
    DaemonConfiguration.adminUsers.map { user =>
      val existingUser = Await.result(usersService.user(user._1, user._2), 1.minutes)
      if (existingUser.isEmpty) Await.result(usersService.createUser(user._1, user._2), 1.minutes)
    }
    DaemonConfiguration.whiteListUsers.map { user =>
      val existingUser = Await.result(usersService.user(user._1), 1.minutes)
      if (existingUser.isEmpty) Await.result(usersService.createUser(user._1, user._2), 1.minutes)
    }
    val scheduler = new ScheduledThreadPoolTimer(
      poolSize = 1,
      threadFactory = new NamedPoolThreadFactory("scheduler-thread-pool")
    )
    scheduler.schedule(
      Time.fromSeconds(DaemonConfiguration.synchronizationInterval._1),
      Duration(DaemonConfiguration.synchronizationInterval._2, TimeUnit.HOURS))(synchronizationTask())
    info(s"Scheduled synchronization job: initial start in ${DaemonConfiguration.synchronizationInterval._1} seconds, " +
      s"interval ${DaemonConfiguration.synchronizationInterval._2} hours")
  }
}
