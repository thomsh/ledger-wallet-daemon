package co.ledger.wallet.daemon.controllers

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, RichRequest}
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.services.CurrenciesService
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{MethodValidation, ValidationResult}
import javax.inject.Inject

import scala.concurrent.ExecutionContext

class CurrenciesController @Inject()(currenciesService: CurrenciesService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import CurrenciesController._

  /**
    * End point queries for currency view with specified name. The name is unique and follows the
    * convention <a href="https://en.wikipedia.org/wiki/List_of_cryptocurrencies">List of cryptocurrencies</a>.
    * Name should be lowercase and predefined by core library.
    *
    */
  get("/pools/:pool_name/currencies/:currency_name") { request: GetCurrencyRequest =>
    val poolName = request.pool_name
    val currencyName = request.currency_name
    info(s"GET currency $request")
    currenciesService.currency(currencyName, poolName, request.user.pubKey).map {
      case Some(currency) => responseSerializer.serializeOk(currency, response)
      case None => responseSerializer.serializeNotFound(
        Map("response" -> "Currency not support", "currency_name" -> currencyName), response)
    }.recover {
      case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> poolName),
        response)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  /**
    * End point queries for currencies view belongs to pool specified by pool name.
    *
    */
  get("/pools/:pool_name/currencies") {request: GetCurrenciesRequest =>
    val poolName = request.pool_name
    info(s"GET currencies $request")
    currenciesService.currencies(poolName, request.user.pubKey).recover {
      case _: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> poolName),
        response)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  /**
    * Endpoint validating the given address. Response boolean value true if is valid,
    * false otherwise.
    *
    */
  get(s"/pools/:pool_name/currencies/:currency_name/validate") { request: AddressValidatingRequest =>
    currenciesService.validateAddress(request.address, request.currency_name, request.pool_name, request.user.pubKey).recover {
      case _ : CurrencyNotFoundException | _: WalletPoolNotFoundException =>
        responseSerializer.serializeBadRequest(Map(
            "response" -> "Wallet pool or currency doesn't exist",
            "pool_name" -> request.pool_name,
            "currency_name" -> request.currency_name),
          response
      )
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }


  }

  private val responseSerializer: ResponseSerializer = ResponseSerializer.newInstance()
}

object CurrenciesController {
  case class AddressValidatingRequest(@RouteParam pool_name: String,
                                      @RouteParam currency_name: String,
                                      @QueryParam address: String,
                                      request: Request) extends RichRequest(request)

  case class GetCurrenciesRequest(
                                 @RouteParam pool_name: String,
                                 request: Request
                                 ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    override def toString: String = s"$request, Parameters(user: ${user.id}, pool_name: $pool_name)"
  }

  case class GetCurrencyRequest(
                               @RouteParam currency_name: String,
                               @RouteParam pool_name: String,
                               request: Request
                               ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

    override def toString: String = s"$request, Parameters(user: ${user.id}, pool_name: $pool_name, currency_name: $currency_name)"
  }
}

