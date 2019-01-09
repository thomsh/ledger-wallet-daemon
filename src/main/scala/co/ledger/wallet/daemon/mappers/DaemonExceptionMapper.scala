package co.ledger.wallet.daemon.mappers

import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder
import javax.inject.{Inject, Singleton}

@Singleton
class DaemonExceptionMapper @Inject()(response: ResponseBuilder)
  extends ExceptionMapper[Exception with DaemonException] {

  private def daemonExceptionInfo(de: DaemonException): Map[String, Any] =
    Map("response" -> de.msg, "error_code" -> de.code)

  override def toResponse(request: Request, throwable: Exception with DaemonException): Response = {
    throwable match {
      case anfe: AccountNotFoundException =>
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(anfe) + ("account_index" -> anfe.accountIndex),
          response)
      case e: OperationNotFoundException =>
        val next = request.getParam("next")
        val previous = request.getParam("previous")
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(e) + ("next_cursor" -> next, "previous_cursor" -> previous),
          response)
      case wnfe: WalletNotFoundException =>
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(wnfe) + ("wallet_name" -> wnfe.walletName),
          response)
      case wpnfe: WalletPoolNotFoundException =>
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(wpnfe) + ("pool_name" -> wpnfe.poolName),
          response)
      case wpaee: WalletPoolAlreadyExistException =>
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(wpaee) + ("pool_name" -> wpaee.poolName),
          response)
      case cnfe: CurrencyNotFoundException =>
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(cnfe) + ("currency_name" -> cnfe.currencyName),
          response)
      case unfe: UserNotFoundException =>
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(unfe) + ("pub_key" -> unfe.pubKey),
          response)
      case uaee: UserAlreadyExistException =>
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(uaee) + ("pub_key" -> uaee.pubKey),
          response)
      case iae: CoreBadRequestException =>
        val walletName = request.getParam("wallet_name")
        val poolName = request.getParam("pool_name")
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(iae) + ("pool_name" -> poolName, "wallet_name" -> walletName),
          response)
      case ssnme: SignatureSizeUnmatchException =>
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(ssnme) + ("tx_size" -> ssnme.txSize, "sig_size" -> ssnme.signatureSize),
          response
        )
      case enfe: ERC20NotFoundException =>
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(enfe) + ("contract" -> enfe.contract),
          response
        )
      case e: ERC20BalanceNotEnough =>
        ResponseSerializer.serializeBadRequest(
          daemonExceptionInfo(e) + ("contract" -> e.tokenAddress),
          response
        )
    }
  }
}
