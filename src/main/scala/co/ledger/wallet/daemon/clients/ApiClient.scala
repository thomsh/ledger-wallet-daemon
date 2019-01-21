package co.ledger.wallet.daemon.clients

import java.net.InetSocketAddress

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.models.FeeMethod
import co.ledger.wallet.daemon.utils.Utils._
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finagle.client.Transporter
import com.twitter.finagle.http.{Method, Request, Response}
import com.twitter.finagle.{Http, Service}
import com.twitter.finatra.json.FinatraObjectMapper
import javax.inject.Singleton

import scala.concurrent.{ExecutionContext, Future}

/**
  * Client for request to blockchain explorers.
  *
  * User: Ting Tu
  * Date: 24-04-2018
  * Time: 14:32
  *
  */
// TODO: Map response from service to be more readable
@Singleton
class ApiClient(implicit val ec: ExecutionContext) {
  import ApiClient._

  def getFees(currencyName: String): Future[FeeInfo] = {
    val path = paths.getOrElse(currencyName, throw new UnsupportedOperationException(s"Currency not supported '$currencyName'"))
    val (host, service) = services.getOrElse(currencyName, services("default"))
    val request = Request(Method.Get, path).host(host)
    service(request).map { response =>
      mapper.parse[FeeInfo](response)
    }.asScala
  }

  def getGasLimit(currencyName: String, recipient: String): Future[BigInt] = {
    val (host, service) = services.getOrElse(currencyName, services("default"))
    val request = Request(Method.Get, s"/blockchain/v3/addresses/$recipient/estimate-gas-limit")
      .host(host)
    service(request).map { response =>
      mapper.parse[GasLimit](response).limit
    }.asScala
  }

  def getGasPrice(currencyName: String): Future[BigInt] = {
    val (host, service) = services.getOrElse(currencyName, services("default"))
    val path = paths.getOrElse(currencyName, throw new UnsupportedOperationException(s"Currency not supported '$currencyName'"))
    val request = Request(Method.Get, path).host(host)

    service(request).map { response =>
      mapper.parse[GasPrice](response).price
    }.asScala
  }

  private val mapper: FinatraObjectMapper = FinatraObjectMapper.create()
  private val client = {
    DaemonConfiguration.proxy match {
      case None =>
        Http.client
          .withSessionPool.maxSize(DaemonConfiguration.explorer.api.connectionPoolSize)
      case Some(proxy) =>
        Http.client
          .withSessionPool.maxSize(DaemonConfiguration.explorer.api.connectionPoolSize)
          .configured(Transporter.HttpProxy(Some(new InetSocketAddress(proxy.host, proxy.port)), None))
    }
  }
  private val services: Map[String,(String, Service[Request, Response])] =
    DaemonConfiguration.explorer.api.paths
      .map { case(currency, path) =>
        val p = path.filterPrefix
        currency -> (p.host, client.newService(s"${p.host}:${p.port}"))
      }

  private val paths: Map[String, String] = {
    Map(
      "bitcoin" -> "/blockchain/v2/btc/fees",
      "bitcoin_testnet" -> "/blockchain/v2/btc_testnet/fees",
      "dogecoin" -> "/blockchain/v2/doge/fees",
      "litecoin" -> "/blockchain/v2/ltc/fees",
      "dash" -> "/blockchain/v2/dash/fees",
      "komodo" -> "/blockchain/v2/kmd/fees",
      "pivx" -> "/blockchain/v2/pivx/fees",
      "viacoin" -> "/blockchain/v2/via/fees",
      "vertcoin" -> "/blockchain/v2/vtc/fees",
      "digibyte" -> "/blockchain/v2/dgb/fees",
      "bitcoin_cash" -> "/blockchain/v2/abc/fees",
      "poswallet" -> "/blockchain/v2/posw/fees",
      "stratis" -> "/blockchain/v2/strat/fees",
      "peercoin" -> "/blockchain/v2/ppc/fees",
      "bitcoin_gold" -> "/blockchain/v2/btg/fees",
      "zcash" -> "/blockchain/v2/zec/fees",
      "ethereum" -> "/blockchain/v3/fees",
      "ethereum_classic" -> "/blockchain/v3/fees",
      "ethereum_ropsten" -> "/blockchain/v3/fees",
    )
  }
}

object ApiClient {
  case class FeeInfo(
                    @JsonProperty("1") fast: BigInt,
                    @JsonProperty("3") normal: BigInt,
                    @JsonProperty("6") slow: BigInt) {

    def getAmount(feeMethod: FeeMethod): BigInt = feeMethod match {
      case FeeMethod.FAST => fast / 1000
      case FeeMethod.NORMAL => normal / 1000
      case FeeMethod.SLOW => slow / 1000
    }
  }

  case class GasPrice(@JsonProperty("gas_price") price: BigInt)
  case class GasLimit(@JsonProperty("estimated_gas_limit") limit: BigInt)
}
