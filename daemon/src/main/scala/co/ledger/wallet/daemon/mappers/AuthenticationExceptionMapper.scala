package co.ledger.wallet.daemon.mappers

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.services.AuthenticationService
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder

@Singleton
class AuthenticationExceptionMapper @Inject()(response: ResponseBuilder)
  extends ExceptionMapper[AuthenticationService.AuthenticationFailedException] {

  override def toResponse(request: Request, throwable: AuthenticationService.AuthenticationFailedException): Response = {
    response.unauthorized(throwable.getMessage)
  }

}
