package co.ledger.wallet.daemon.controllers

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, RequestWithUser}
import co.ledger.wallet.daemon.models.{AccountInfo, FeeMethod}
import co.ledger.wallet.daemon.services.TransactionsService
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import com.twitter.finatra.validation.{MethodValidation, ValidationResult}
import javax.inject.Inject

import scala.concurrent.ExecutionContext

/**
  * The controller for transaction operations.
  *
  * User: Ting Tu
  * Date: 24-04-2018
  * Time: 11:07
  *
  */
class TransactionsController @Inject()(transactionsService: TransactionsService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  import TransactionsController._

  /**
    * Transaction creation method.
    * Input json
    * {
    * recipient: recipient address,
    * fees_per_byte: optional(in satoshi),
    * fees_level: optional(SLOW, FAST, NORMAL),
    * amount: in satoshi,
    * exclude_utxos: map{txHash: index}
    * }
    *
    */
  post("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index/transactions") { request: AccountInfoRequest =>
    info(s"Create transaction $request: ${request.request.contentString}")
    transactionsService.createTransaction(request.request, request.accountInfo)
  }

  /**
    * Send a signed transaction.
    * Input json
    * {
    * raw_transaction: the bytes,
    * signatures: [string],
    * pubkeys: [string]
    * }
    */
  post("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index/transactions/sign") { request: AccountInfoRequest =>
    info(s"Sign transaction $request: ${request.request.contentString}")
    transactionsService.broadcastTransaction(request.request, request.accountInfo)
  }

}

object TransactionsController {

  case class AccountInfoRequest(@RouteParam pool_name: String,
                                @RouteParam wallet_name: String,
                                @RouteParam account_index: Int,
                                request: Request
                               ) extends RequestWithUser
  {
    def accountInfo = AccountInfo(account_index, wallet_name, pool_name, user.pubKey)
  }

  trait BroadcastTransactionRequest

  case class BroadcastETHTransactionRequest(
                                           raw_transaction: String,
                                           signatures: Seq[String],
                                           request: Request
                                         ) extends BroadcastTransactionRequest {
    def hexTx: Array[Byte] = HexUtils.valueOf(raw_transaction)
    def hexSig: Array[Byte] = HexUtils.valueOf(signatures.head)

    @MethodValidation
    def validateSignatures: ValidationResult = ValidationResult.validate(
      signatures.size == 1,
      s"expecting 1 DER signature, found ${signatures.size} instead."
    )
  }

  case class BroadcastBTCTransactionRequest(
                                           raw_transaction: String,
                                           signatures: Seq[String],
                                           pubkeys: Seq[String],
                                           request: Request
                                         ) extends BroadcastTransactionRequest {
    def rawTx: Array[Byte] = HexUtils.valueOf(raw_transaction)
    def pairedSignatures: Seq[(Array[Byte], Array[Byte])] = signatures.zipWithIndex.map { case (sig, index) =>
      (HexUtils.valueOf(sig), HexUtils.valueOf(pubkeys(index)))
    }

    @MethodValidation
    def validateSignatures: ValidationResult = ValidationResult.validate(
      signatures.size == pubkeys.size,
      "signatures and pubkeys size not matching")
  }

  trait CreateTransactionRequest {
    def transactionInfo: TransactionInfo
  }

  case class CreateETHTransactionRequest(recipient: String,
                                         amount: Long,
                                         gas_limit: Long,
                                         gas_price: Long,
                                         contract: Option[String]
                                        ) extends CreateTransactionRequest {
    override def transactionInfo: TransactionInfo = ETHTransactionInfo(recipient, amount, gas_limit, gas_price, contract)
  }

  case class CreateBTCTransactionRequest(recipient: String,
                                         fees_per_byte: Option[Long],
                                         fees_level: Option[String],
                                         amount: Long,
                                         exclude_utxos: Option[Map[String, Int]],
                                        ) extends CreateTransactionRequest {

    def transactionInfo: BTCTransactionInfo = BTCTransactionInfo(recipient, fees_per_byte, fees_level, amount, exclude_utxos.getOrElse(Map[String, Int]()))

    @MethodValidation
    def validateFees: ValidationResult = CommonMethodValidations.validateFees(fees_per_byte, fees_level)
  }

  trait TransactionInfo

  case class BTCTransactionInfo(recipient: String, feeAmount: Option[Long], feeLevel: Option[String], amount: Long, excludeUtxos: Map[String, Int]) extends TransactionInfo {
    lazy val feeMethod: Option[FeeMethod] = feeLevel.map { level => FeeMethod.from(level) }
  }

  case class ETHTransactionInfo(recipient: String, amount: Long, gasLimit: Long, gasPrice: Long, contract: Option[String]) extends TransactionInfo
}
