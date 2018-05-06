package co.ledger.wallet.daemon.clients

import java.util.concurrent.{Executors, TimeUnit}

import co.ledger.core.{ErrorCode, WebSocketConnection}
import com.twitter.concurrent.NamedPoolThreadFactory
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit


class ScalaWebSocketClientTest extends AssertionsForJUnit {

  @Test def verifyThreadedConnection(): Unit = {
    val client = ClientFactory.webSocketClient
    val nThreads = 10
    val executor = Executors.newFixedThreadPool(nThreads, new NamedPoolThreadFactory("scala-web-socket-client-test"))
    val connect = new Runnable {
      override def run(): Unit = {
        val connection = new TestWebSocketConnection
        client.connect("ws://echo.websocket.org", connection)
        client.send(connection, "It's me " + connection.getConnectionId)
        client.disconnect(connection)
      }
    }
    (0 to nThreads).foreach(_ => executor.submit(connect))

    executor.shutdown()
    val done = executor.awaitTermination(20, TimeUnit.SECONDS)
    assert(done)

  }

  class TestWebSocketConnection extends WebSocketConnection {
    override def onConnect(connectionId: Int): Unit = {
      conId = connectionId
      println(s"Connected with id $connectionId")
    }

    override def onClose(): Unit = println("Close connection")

    override def onMessage(data: String): Unit = println(s"Echo message: $data")

    override def onError(code: ErrorCode, message: String): Unit = println(s"Error: $code, Message: $message")

    override def getConnectionId: Int = conId

    private var conId: Int = -1
  }
}
