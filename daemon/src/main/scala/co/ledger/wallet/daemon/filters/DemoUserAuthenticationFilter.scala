package co.ledger.wallet.daemon.filters

import java.nio.charset.StandardCharsets
import java.util.Date
import javax.inject.Inject

import co.ledger.wallet.daemon.services.AuthenticationService.{AuthContext, AuthContextContext}
import co.ledger.wallet.daemon.services.ECDSAService
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import org.bitcoinj.core.Sha256Hash

class DemoUserAuthenticationFilter @Inject()(ecdsa: ECDSAService) extends SimpleFilter[Request, Response] {

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    request.headerMap.get("authorization").filter(_ contains "Basic") foreach {(string) =>
      val privKey = Sha256Hash.hash(string.getBytes(StandardCharsets.UTF_8))
      val pk = ecdsa.computePublicKey(privKey)
      val time = new Date().getTime / 1000
      val message = Sha256Hash.hash(s"LWD: $time\n".getBytes(StandardCharsets.UTF_8))
      val signedMessage = ecdsa.sign(message, privKey)
      AuthContextContext.setContext(request, AuthContext(pk, time, signedMessage))
    }
    service(request)
  }
}
