package co.ledger.wallet.daemon

import co.ledger.wallet.daemon.controllers._
import co.ledger.wallet.daemon.filters._
import co.ledger.wallet.daemon.mappers._
import co.ledger.wallet.daemon.modules.{DaemonCacheModule, DaemonJacksonModule}
import co.ledger.wallet.daemon.utils.NativeLibLoader
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{AccessLoggingFilter, CommonFilters, LoggingMDCFilter, TraceIdMDCFilter}
import com.twitter.finatra.http.routing.HttpRouter

object Server extends ServerImpl {

}

class ServerImpl extends HttpServer {

  override def jacksonModule = DaemonJacksonModule

  override val modules = Seq(DaemonCacheModule)

  override protected def configureHttp(router: HttpRouter): Unit =
    router
      .filter[LoggingMDCFilter[Request, Response]]
      .filter[TraceIdMDCFilter[Request, Response]]
      .filter[CorrelationIdFilter[Request, Response]]
      .filter[CommonFilters]
      .filter[AccessLoggingFilter[Request]]
      .filter[DemoUserAuthenticationFilter]
      .filter[LWDAutenticationFilter]
      .add[AuthenticationFilter, AccountsController]
      .add[AuthenticationFilter, CurrenciesController]
      .add[StatusController]
      .add[AuthenticationFilter, WalletPoolsController]
      .add[AuthenticationFilter, WalletsController]
      .add[AuthenticationFilter, TransactionsController]
      // IMPORTANT: pay attention to the order, the latter mapper override the former mapper
      .exceptionMapper[AuthenticationExceptionMapper]
      .exceptionMapper[DaemonExceptionMapper]
      .exceptionMapper[LibCoreExceptionMapper]

  override protected def warmup(): Unit = {
    super.warmup()
    NativeLibLoader.loadLibs()
  }
}
