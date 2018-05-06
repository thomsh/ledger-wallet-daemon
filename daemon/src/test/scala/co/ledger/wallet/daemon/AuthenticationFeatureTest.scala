package co.ledger.wallet.daemon

import java.nio.charset.StandardCharsets
import java.util.{Base64, Date}

import co.ledger.wallet.daemon.services.ECDSAService
import co.ledger.wallet.daemon.utils.{FixturesUtils, HexUtils}
import com.twitter.finagle.http.Status
import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import org.bitcoinj.core.Sha256Hash

class AuthenticationFeatureTest extends FeatureTest {
  override val server = new EmbeddedHttpServer(new ServerImpl)

  test("Authentication#Basic Authentication for demo users") {
    server.httpGet(path = "/_health", headers = basicAuthorisationHeader("admin", "password"))
  }

  test("Authentication#Basic Authentication with wrong demo user") {
    server.httpGet(path = "/_health", headers = basicAuthorisationHeader("nil", "void"), andExpect = Status.Unauthorized)
  }

  test("Authentication#Authenticate with LWD whitelisted") {
    server.httpGet(path = "/_health", headers = lwdBasicAuthorisationHeader("whitelisted"))
  }

  test("Authentication#Authenticate with LWD whitelisted and valid timestamp (after now)") {
    server.httpGet(path = "/_health", headers = lwdBasicAuthorisationHeader("whitelisted", new Date(new Date().getTime + 10000)))
  }

  test("Authentication#Authenticate with LWD backlisted") {
    server.httpGet(path = "/_health", headers = lwdBasicAuthorisationHeader("blacklisted"), andExpect = Status.Unauthorized)
  }

  test("Authentication#Authenticate with LWD whitelisted and invalid timestamp (before now)") {
    server.httpGet(path = "/_health", headers = lwdBasicAuthorisationHeader("whitelisted", new Date(new Date().getTime - 60000)), andExpect = Status.Unauthorized)
  }

  test("Authentication#Authenticate with LWD whitelisted and invalid timestamp (after now)") {
    server.httpGet(path = "/_health", headers = lwdBasicAuthorisationHeader("whitelisted", new Date(new Date().getTime + 60000)), andExpect = Status.Unauthorized)
  }

  test("Authentication#Missing authorization") {
    server.httpGet(path = "/_health", headers = Map[String, String](), andExpect = Status.Unauthorized)
  }

  test("Authentication#Not authorized") {
    server.httpGet(path = "/_health", headers = invalidLWDAuthorisationHeader("whitelisted"), andExpect = Status.Unauthorized)
  }

  private def basicAuthorisationHeader(username: String, password: String) = Map(
    "authorization" -> s"Basic ${Base64.getEncoder.encodeToString(s"$username:$password".getBytes(StandardCharsets.UTF_8))}"
  )

  private def invalidLWDAuthorisationHeader(seedName: String) = {
    val ecdsa = server.injector.instance(classOf[ECDSAService])
    val privKey = Sha256Hash.hash(FixturesUtils.seed(seedName).getBytes)
    val pubKey = ecdsa.computePublicKey(privKey)
    val timestamp = new Date().getTime / 1000
    val message = Sha256Hash.hash(s"LWD: $timestamp\n".getBytes)
    val signed = ecdsa.sign(message, privKey)
    Map(
      "authorization" -> s"LW ${Base64.getEncoder.encodeToString(s"${HexUtils.valueOf(pubKey)}:$timestamp:${HexUtils.valueOf(signed)}".getBytes(StandardCharsets.UTF_8))}"
    )
  }

  private def lwdBasicAuthorisationHeader(seedName: String, time: Date = new Date()) = {
    val ecdsa = server.injector.instance(classOf[ECDSAService])
    val privKey = Sha256Hash.hash(FixturesUtils.seed(seedName).getBytes)
    val pubKey = ecdsa.computePublicKey(privKey)
    val timestamp = time.getTime / 1000
    val message = Sha256Hash.hash(s"LWD: $timestamp\n".getBytes)
    val signed = ecdsa.sign(message, privKey)
    Map(
      "authorization" -> s"LWD ${Base64.getEncoder.encodeToString(s"${HexUtils.valueOf(pubKey)}:$timestamp:${HexUtils.valueOf(signed)}".getBytes(StandardCharsets.UTF_8))}"
    )
  }
}
