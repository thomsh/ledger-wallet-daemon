package co.ledger.wallet.daemon.clients

import java.net.InetSocketAddress

import javax.inject.Singleton
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.models.FeeMethod
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.finagle.Http
import co.ledger.wallet.daemon.utils.Utils._
import com.twitter.finagle.client.Transporter
import com.twitter.finagle.http.{Method, Request}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Client for request to blockchain explorers.
  *
  * User: Ting Tu
  * Date: 24-04-2018
  * Time: 14:32
  *
  */
@Singleton
class ApiClient(implicit val ec: ExecutionContext) {
  import ApiClient._
  private[this] val (proxyEnabled, proxyHost, proxyPort) = DaemonConfiguration.proxy
  private[this] val hostServices = DaemonConfiguration.explorerApiAddresses.map { case (currencyName, (host, port, poolSize)) =>
    // This is required because Http.Client.newService fails if the url starts with the protocol
    val hostNoProtocol = host.replaceFirst(".+?://", "")
    (currencyName, (hostNoProtocol, buildClient(poolSize).newService(s"$hostNoProtocol:$port")))
  }

  //TODO: support dynamically
  def getFees(currencyName: String): Future[FeeInfo] = {
    val path = currencyName match {
      case "bitcoin" => "/blockchain/v2/btc/fees"
      case "bitcoin_testnet" => "/blockchain/v2/btc_testnet/fees"
      case "dogecoin" => "/blockchain/v2/doge/fees"
      case "litecoin" => "/blockchain/v2/ltc/fees"
      case "dash" => "/blockchain/v2/dash/fees"
      case "komodo" => "/blockchain/v2/kmd/fees"
      case "pivx" => "/blockchain/v2/pivx/fees"
      case "viacoin" => "/blockchain/v2/via/fees"
      case "vertcoin" => "/blockchain/v2/vtc/fees"
      case "digibyte" => "/blockchain/v2/dgb/fees"
      case "bitcoin_cash" => "/blockchain/v2/abc/fees"
      case "poswallet" => "/blockchain/v2/posw/fees"
      case "stratis" => "/blockchain/v2/strat/fees"
      case "peercoin" => "/blockchain/v2/ppc/fees"
      case "bitcoin_gold" => "/blockchain/v2/btg/fees"
      case "zcash" => "/blockchain/v2/zec/fees"

      case _ => throw new UnsupportedOperationException(s"currency not supported '$currencyName'")
    }
    val request = Request(Method.Get, path)
    val (host, service) = hostServices.getOrElse(currencyName, hostServices("default"))
    request.host = host

    service(request).map { response =>
      mapper.readValue(response.contentString, classOf[FeeInfo])
    }.asScala
  }

  def getGasLimit(currencyName: String, recipient: String): Future[BigInt] = {
    val (host, service) = hostServices.getOrElse(currencyName, hostServices("default"))
    val request = Request(Method.Get, s"/blockchain/v3/addresses/$recipient/estimate-gas-limit")
    request.host = host

    service(request).map { response =>
      mapper.readValue(response.contentString, classOf[GasInfo]).limit
    }.asScala
  }

  def getGasPrice(currencyName: String): Future[BigInt] = {
    val (host, service) = hostServices.getOrElse(currencyName, hostServices("default"))
    val request = Request(Method.Get, "/blockchain/v3/fees")
    request.host = host

    service(request).map { response =>
      mapper.readValue(response.contentString, classOf[GasInfo]).price
    }.asScala
  }

  private def buildClient(poolSize: Int): Http.Client = {
    var client = Http.client.withSessionPool.maxSize(poolSize)
    if (proxyEnabled) {
      client = client.configured(Transporter.HttpProxy(Some(new InetSocketAddress(proxyHost, proxyPort)), None))
    }
    client
  }

  private val mapper: ObjectMapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
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

  case class GasInfo(@JsonProperty("gas_price") price: BigInt,
                     @JsonProperty("estimated_gas_limit") limit: BigInt)
}
