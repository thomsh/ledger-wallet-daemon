package co.ledger.wallet.daemon.filters

import javax.inject.Singleton

import com.twitter.finagle.tracing.Trace
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import org.slf4j.MDC

@Singleton
class TraceIdMDCFilter[Req, Rep] extends SimpleFilter[Req, Rep] {

  override def apply(request: Req, service: Service[Req, Rep]): Future[Rep] = {
    MDC.put("traceId", Trace.id.traceId.toString())
    service(request)
  }
}
