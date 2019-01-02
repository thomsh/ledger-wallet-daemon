package co.ledger.wallet.daemon.filters

import javax.inject.Inject

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.services.AuthenticationService
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future

class AuthenticationFilter @Inject()(authService: AuthenticationService) extends SimpleFilter[Request, Response] {

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    authService.authorize(request) flatMap {(_) =>
      service(request)
    }
  }

}
