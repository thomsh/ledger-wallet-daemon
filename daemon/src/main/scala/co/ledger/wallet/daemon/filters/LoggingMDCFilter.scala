package co.ledger.wallet.daemon.filters

import javax.inject.Singleton

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import org.slf4j.{FinagleMDCInitializer, MDC}

@Singleton
class LoggingMDCFilter[Req, Rep] extends SimpleFilter[Req, Rep] {

  FinagleMDCInitializer.init()

  override def apply(request: Req, service: Service[Req, Rep]): Future[Rep] = {
    service(request).ensure {
      MDC.clear()
    }
  }
}
