package co.ledger.wallet.daemon.mappers

import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder
import javax.inject.{Inject, Singleton}

@Singleton
class DaemonExceptionMapper @Inject()(response: ResponseBuilder)
  extends ExceptionMapper[DaemonException] {
  override def toResponse(request: Request, throwable: DaemonException): Response = {
    throwable match {
      case anfe: AccountNotFoundException =>
        ResponseSerializer.serializeBadRequest(
          Map("response" -> "Account doesn't exist", "account_index" -> anfe.accountIndex),
          response)
      case onfe: OperationNotFoundException =>
        val next = request.getParam("next")
        val previous = request.getParam("previous")
        ResponseSerializer.serializeBadRequest(
          Map("response" -> "Operation cursor doesn't exist", "next_cursor" -> next, "previous_cursor" -> previous),
          response)
      case wnfe: WalletNotFoundException =>
        ResponseSerializer.serializeBadRequest(
          Map("response" -> "Wallet doesn't exist", "wallet_name" -> wnfe.walletName),
          response)
      case wpnfe: WalletPoolNotFoundException =>
        ResponseSerializer.serializeBadRequest(
          Map("response" -> "Wallet pool doesn't exist", "pool_name" -> wpnfe.poolName),
          response)
      case wpaee: WalletPoolAlreadyExistException =>
        ResponseSerializer.serializeBadRequest(
          Map("response" -> wpaee.getMessage, "pool_name" -> wpaee.poolName),
          response)
      case cnfe: CurrencyNotFoundException =>
        ResponseSerializer.serializeBadRequest(
          Map("response" -> "Currency not support", "currency_name" -> cnfe.currencyName),
          response)
      case unfe: UserNotFoundException =>
        ResponseSerializer.serializeBadRequest(
          Map("response" -> unfe.getMessage, "pub_key" -> unfe.pubKey),
          response)
      case uaee: UserAlreadyExistException =>
        ResponseSerializer.serializeBadRequest( Map("response" -> uaee.getMessage, "pub_key" -> uaee.pubKey),
          response)
      case iae: CoreBadRequestException =>
        val walletName = request.getParam("wallet_name")
        val poolName = request.getParam("pool_name")
        ResponseSerializer.serializeBadRequest(
          Map("response" -> iae.msg, "pool_name" -> poolName, "wallet_name" -> walletName),
          response)
      case ssnme: SignatureSizeUnmatchException =>
        ResponseSerializer.serializeBadRequest(
          Map("response" -> ssnme.getMessage, "tx_size" -> ssnme.txSize, "sig_size" -> ssnme.signatureSize),
          response
        )
      case enfe: ERC20NotFoundException =>
        ResponseSerializer.serializeBadRequest(
          Map("response" -> enfe.getMessage, "contract" -> enfe.contract),
          response
        )
      case e: ERC20BalanceNotEnough =>
        ResponseSerializer.serializeBadRequest(
          Map("response" -> e.getMessage, "contract" -> e.tokenAddress),
          response
        )
    }
  }
}
