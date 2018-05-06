package co.ledger.wallet.daemon.controllers.requests

import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._
import com.twitter.finagle.http.Request

class RichRequest(request: Request) {
  def user: User = request.user.get

  override def toString: String = s"$request, Parameters(user: ${user.id})"
}
