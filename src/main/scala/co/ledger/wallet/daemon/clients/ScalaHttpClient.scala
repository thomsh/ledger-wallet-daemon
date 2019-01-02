package co.ledger.wallet.daemon.clients

import java.io.{BufferedInputStream, DataOutputStream}
import java.net.{HttpURLConnection, InetSocketAddress, URL}
import java.util

import co.ledger.core.{ErrorCode, HttpMethod, HttpReadBodyResult, HttpRequest}
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream
import com.twitter.inject.Logging

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

//noinspection ScalaStyle
class ScalaHttpClient(implicit val ec: ExecutionContext) extends co.ledger.core.HttpClient with Logging {
import ScalaHttpClient._

  override def execute(request: HttpRequest): Unit = Future {
    val (proxyEnabled, proxyHost, proxyPort) = DaemonConfiguration.proxy
    val connection = if(proxyEnabled) {
      val proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort))
      new URL(request.getUrl).openConnection(proxy).asInstanceOf[HttpURLConnection]
    } else {
      new URL(request.getUrl).openConnection().asInstanceOf[HttpURLConnection]
    }
    connection.setRequestMethod(resolveMethod(request.getMethod))
    for ((key, value) <- request.getHeaders.asScala) {
       connection.setRequestProperty(key, value)
    }
    connection.setRequestProperty("Content-Type", "application/json")
    info(s"${request.getMethod} ${request.getUrl}")
    val body = request.getBody
    if (body.nonEmpty) {
      connection.setDoOutput(true)
      val dataOs = new DataOutputStream(connection.getOutputStream)
      dataOs.write(body)
      dataOs.flush()
      dataOs.close()
    }
    val statusCode = connection.getResponseCode
    val statusText = connection.getResponseMessage
    val isError = !(statusCode >= 200 && statusCode < 400)
    val response =
      new BufferedInputStream(if (!isError) connection.getInputStream else connection.getErrorStream)
    val headers = new util.HashMap[String, String]()
    for ((key, list) <- connection.getHeaderFields.asScala) {
      headers.put(key, list.get(list.size() - 1))
    }
    val proxy = new co.ledger.core.HttpUrlConnection() {
      private val buffer = new Array[Byte](PROXY_BUFFER_SIZE)

      override def getStatusCode: Int = statusCode

      override def getStatusText: String = statusText

      override def getHeaders: util.HashMap[String, String] = headers

      override def readBody(): HttpReadBodyResult = {
        Try {
          val outputStream = new ByteOutputStream()
          var size = 0
          do {
            size = response.read(buffer)
            if (size < buffer.length) {
              outputStream.write(buffer.slice(0, size))
            } else {
              outputStream.write(buffer)
            }
          } while (size > 0)
          outputStream.getBytes
        } match {
          case Success(data) =>
            if (isError) info(s"Received error ${new String(data)}")
            new HttpReadBodyResult(null, data)
          case Failure(ex) =>
            ex.printStackTrace()
            val error = new co.ledger.core.Error(ErrorCode.HTTP_ERROR, "An error happened during body reading.")
            new HttpReadBodyResult(error, null)
        }
      }
    }
    request.complete(proxy, null)
  }.failed.map[co.ledger.core.Error]({
    others: Throwable =>
      new co.ledger.core.Error(ErrorCode.HTTP_ERROR, others.getMessage)
  }).foreach(request.complete(null, _))

  private def resolveMethod(method: HttpMethod) = method match {
    case HttpMethod.GET => "GET"
    case HttpMethod.POST => "POST"
    case HttpMethod.PUT => "PUT"
    case HttpMethod.DEL => "DELETE"
  }
}

object ScalaHttpClient {
  val PROXY_BUFFER_SIZE: Int = 4*4096
}
