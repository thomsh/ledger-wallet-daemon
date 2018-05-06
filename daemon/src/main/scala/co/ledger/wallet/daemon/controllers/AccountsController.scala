package co.ledger.wallet.daemon.controllers

import java.util.UUID
import javax.inject.Inject

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, RichRequest}
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.filters.AccountCreationContext._
import co.ledger.wallet.daemon.filters.AccountCreationFilter
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._
import co.ledger.wallet.daemon.services.{AccountsService, OperationQueryParams}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{MethodValidation, ValidationResult}

import scala.concurrent.ExecutionContext

class AccountsController @Inject()(accountsService: AccountsService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  import AccountsController._

  /**
    * End point queries for account views with specified pool name and wallet name.
    *
    */
  get("/pools/:pool_name/wallets/:wallet_name/accounts") { request: AccountsRequest =>
    info(s"GET accounts $request")
    accountsService.accounts(request.user, request.pool_name, request.wallet_name).recover {
      case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response)
      case _: WalletNotFoundException => responseSerializer.serializeBadRequest(
        Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name),
        response)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  /**
    * End point queries for derivation information view of next account creation.
    *
    */
  get("/pools/:pool_name/wallets/:wallet_name/accounts/next") { request: AccountCreationInfoRequest =>
    info(s"GET account creation info $request")
    accountsService.nextAccountCreationInfo(request.user, request.pool_name, request.wallet_name, request.account_index).recover {
      case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response)
      case _: WalletNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet doesn't exist", "wallet_name" -> request.wallet_name),
        response)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  /**
    * End point queries for account view with specified pool, wallet name, and unique account index.
    *
    */
  get("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index") { request: AccountRequest =>
    info(s"GET account $request")
    accountsService.account(request.account_index, request.user, request.pool_name, request.wallet_name).map {
      case Some(view) => responseSerializer.serializeOk(view, response)
      case None => responseSerializer.serializeNotFound(Map("response" -> "Account doesn't exist", "account_index" -> request.account_index), response)
    }.recover {
      case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response)
      case _: WalletNotFoundException => responseSerializer.serializeBadRequest(
        Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name),
        response)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  /**
    * End point queries for fresh addresses with specified pool, wallet name and unique account index.
    *
    */
  get("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index/addresses/fresh") { request: AccountRequest =>
    info(s"GET fresh addresses $request")
    accountsService.accountFreshAddresses(request.account_index, request.user, request.pool_name, request.wallet_name).recover {
      case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response)
      case _: WalletNotFoundException => responseSerializer.serializeBadRequest(
        Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name),
        response)
      case _: AccountNotFoundException => responseSerializer.serializeBadRequest(
        Map("response"->"Account doesn't exist", "account_index" -> request.account_index),
        response)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  /**
    * End point queries for operation views with specified pool, wallet name, and unique account index.
    *
    */
  get("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index/operations") { request: OperationsRequest =>
    info(s"GET account operations $request")
    accountsService.accountOperations(
      request.user,
      request.account_index,
      request.pool_name,
      request.wallet_name,
      OperationQueryParams(request.previous, request.next, request.batch, request.full_op)).recover {
        case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
          Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
          response)
        case _: OperationNotFoundException => responseSerializer.serializeBadRequest(
          Map("response" -> "Operation cursor doesn't exist", "next_cursor" -> request.next, "previous_cursor" -> request.previous),
          response)
        case _: WalletNotFoundException => responseSerializer.serializeBadRequest(
          Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name),
          response)
        case _: AccountNotFoundException => responseSerializer.serializeBadRequest(
          Map("response"->"Account doesn't exist", "account_index" -> request.account_index),
          response)
        case e: Throwable => responseSerializer.serializeInternalError(response, e)
      }
  }

  /**
    * End point queries for operation view with specified uid.
    *
    */
  get("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index/operations/:uid") { request: OperationResult =>
    info(s"GET account operation $request")
    accountsService.accountOperation(request.user, request.uid, request.account_index, request.pool_name, request.wallet_name, request.full_op).map {
      case Some(view) => responseSerializer.serializeOk(view, response)
      case None => responseSerializer.serializeNotFound(Map("response" -> "Account operation doesn't exist", "uid" -> request.uid), response)
    }.recover {
      case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response)
      case _: WalletNotFoundException => responseSerializer.serializeBadRequest(
        Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name),
        response)
      case _: AccountNotFoundException => responseSerializer.serializeBadRequest(
        Map("response"->"Account doesn't exist", "account_index" -> request.account_index),
        response)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  /**
    * End point to create a new account within the specified pool and wallet.
    *
    */
  filter[AccountCreationFilter]
    .post("/pools/:pool_name/wallets/:wallet_name/accounts") { request: Request =>
      val walletName = request.getParam("wallet_name")
      val poolName = request.getParam("pool_name")
      info(s"CREATE account $request, " +
        s"Parameters(user: ${request.user.get.id}, pool_name: $poolName, wallet_name: $walletName), " +
        s"Body(${request.accountCreationBody}")
      accountsService.createAccount(request.accountCreationBody,request.user.get,poolName,walletName).recover {
        case iae: InvalidArgumentException => responseSerializer.serializeBadRequest(
          Map("response"-> iae.msg, "pool_name" -> poolName, "wallet_name"->walletName),
          response)
        case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
          Map("response" -> "Wallet pool doesn't exist", "pool_name" -> poolName),
          response)
        case _: WalletNotFoundException => responseSerializer.serializeBadRequest(
          Map("response"->"Wallet doesn't exist", "wallet_name" -> walletName),
          response)
        case e: Throwable => responseSerializer.serializeInternalError(response, e)
      }
  }

  private val responseSerializer: ResponseSerializer = ResponseSerializer.newInstance()
}

object AccountsController {
  private val DEFAULT_BATCH: Int = 20
  private val DEFAULT_OPERATION_MODE: Int = 0
  case class AccountRequest(
                           @RouteParam pool_name: String,
                           @RouteParam wallet_name: String,
                           @RouteParam account_index: Int,
                           request: Request
                           ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validateAccountIndex: ValidationResult = ValidationResult.validate(account_index >= 0, "account_index: index can not be less than zero")

    override def toString: String = s"$request, Parameters(user: ${user.id}, pool_name: $pool_name, wallet_name: $wallet_name, account_index: $account_index)"
  }

  case class AccountsRequest(
                            @RouteParam pool_name: String,
                            @RouteParam wallet_name: String,
                            request: Request
                            ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)

    override def toString: String = s"$request, Parameters(user: ${user.id}, pool_name: $pool_name, wallet_name: $wallet_name)"
  }

  case class AccountCreationInfoRequest(
                                         @RouteParam pool_name: String,
                                         @RouteParam wallet_name: String,
                                         @QueryParam account_index: Option[Int],
                                         request: Request
                                       ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validateAccountIndex: ValidationResult = CommonMethodValidations.validateOptionalAccountIndex(account_index)

    override def toString: String = s"$request, Parameters(user: ${user.id}, pool_name: $pool_name, wallet_name: $wallet_name, account_index: $account_index)"
  }

  case class OperationsRequest(
                             @RouteParam pool_name: String,
                             @RouteParam wallet_name: String,
                             @RouteParam account_index: Int,
                             @QueryParam next: Option[UUID],
                             @QueryParam previous: Option[UUID],
                             @QueryParam batch: Int = DEFAULT_BATCH,
                             @QueryParam full_op: Int = DEFAULT_OPERATION_MODE,
                             request: Request
                             ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validateAccountIndex: ValidationResult = ValidationResult.validate(account_index >= 0, "account_index: index can not be less than zero")

    @MethodValidation
    def validateBatch: ValidationResult = ValidationResult.validate(batch > 0, "batch: batch should be greater than zero")

    override def toString: String = s"$request, Parameters(" +
      s"user: ${user.id}, " +
      s"pool_name: $pool_name, " +
      s"wallet_name: $wallet_name, " +
      s"account_index: $account_index, " +
      s"next: $next, " +
      s"previous: $previous, " +
      s"batch: $batch, " +
      s"full_op: $full_op)"
  }

  case class OperationResult(
                            @RouteParam pool_name: String,
                            @RouteParam wallet_name: String,
                            @RouteParam account_index: Int,
                            @RouteParam uid: String,
                            @QueryParam full_op: Int = 0,
                            request: Request
                            ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName: ValidationResult = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validateAccountIndex: ValidationResult = ValidationResult.validate(account_index >= 0, "account_index: index can not be less than zero")

    override def toString: String = s"$request, Parameters(" +
      s"user: ${user.id}, pool_name: $pool_name, wallet_name: $wallet_name, account_index: $account_index, uid: $uid, full_op: $full_op)"
  }
}