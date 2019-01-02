package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.controllers.responses.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.models.{WalletView, WalletsViewWithCount}
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}

class WalletsApiTest extends APIFeatureTest {

  test("WalletsApi#Create then Get same wallet from pool Return OK") {
    createPool(WALLET_POOL)
    val createdW =  walletFromResponse(assertWalletCreation(WALLET_POOL, "my_wallet", "bitcoin", Status.Ok))
    assert("my_wallet" === createdW.name)
    assert("bitcoin" === createdW.currency.name)
    val getW =  walletFromResponse(assertGetWallet(WALLET_POOL, "my_wallet", Status.Ok))
    assert(createdW === getW)
    deletePool(WALLET_POOL)
  }

  test("WalletsApi#Create then Get same wallet from pool Return OK (for bitcoin testnet)") {
    createPool(WALLET_POOL)
    val createdW =  walletFromResponse(assertWalletCreation(WALLET_POOL, "my_testnet_wallet", "bitcoin_testnet", Status.Ok))
    assert("my_testnet_wallet" === createdW.name)
    assert("bitcoin_testnet" === createdW.currency.name)
    val getW =  walletFromResponse(assertGetWallet(WALLET_POOL, "my_testnet_wallet", Status.Ok))
    assert(createdW === getW)
    deletePool(WALLET_POOL)
  }

  test("WalletsApi#Get non exist wallet from existing pool Return Not Found") {
    createPool(WALLET_POOL)
    val notFoundErr = server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertGetWallet(WALLET_POOL, "not_exist_wallet", Status.NotFound).contentString)
    assert(notFoundErr === ErrorResponseBody(ErrorCode.Not_Found, Map("response"->"Wallet doesn't exist","wallet_name"->"not_exist_wallet")))
    deletePool(WALLET_POOL)
  }

  test("WalletsApi#Create already exist wallet Return Ok") {
    createPool("duplicate_pool")
    val createdW = walletFromResponse(assertWalletCreation("duplicate_pool", "duplicate_wallet", "bitcoin", Status.Ok))
    assert("duplicate_wallet" === createdW.name)
    assert(Map[String, Any]() === createdW.configuration)
    assert("bitcoin" === createdW.currency.name)
    val createdIgnore = walletFromResponse(assertWalletCreation("duplicate_pool", "duplicate_wallet", "bitcoin", Status.Ok))
    assert(createdW === createdIgnore)
    deletePool("duplicate_pool")
  }

  test("WalletsApi#Get two wallets from pool Return OK") {
    createPool("multi_pool")
    val wallet1 = walletFromResponse(assertWalletCreation("multi_pool", "wallet_1", "bitcoin", Status.Ok))
    val wallet2 = walletFromResponse(assertWalletCreation("multi_pool", "wallet_2", "bitcoin", Status.Ok))
    val wallet3 = walletFromResponse(assertWalletCreation("multi_pool", "wallet_3", "bitcoin", Status.Ok))
    val getW = walletsFromResponse(assertGetWallets("multi_pool", 1, 2, Status.Ok))
    assert(3 === getW.count)
    assert(WalletsViewWithCount(3, List(wallet2, wallet3)) === getW)
    deletePool("multi_pool")
  }

  test("WalletsApi#Get wallets with invalid offset and batch size") {
    createPool("multi_pool_mal")
    val wallet1 = walletFromResponse(assertWalletCreation("multi_pool_mal", "wallet_1", "bitcoin", Status.Ok))
    val wallet2 = walletFromResponse(assertWalletCreation("multi_pool_mal", "wallet_2", "bitcoin", Status.Ok))
    val wallet3 = walletFromResponse(assertWalletCreation("multi_pool_mal", "wallet_3", "bitcoin", Status.Ok))
    assertGetWallets("multi_pool_mal", 1, -2, Status.BadRequest)
    assertGetWallets("multi_pool_mal", -1, 2, Status.BadRequest)
    deletePool("multi_pool_mal")
  }

  test("WalletsApi#Get no wallets from existing pool") {
    createPool("empty_pool")
    val result = assertGetWallets("empty_pool", 0, 2, Status.Ok)
    assert(WalletsViewWithCount(0, Array[WalletView]()) === walletsFromResponse(result))
    deletePool("empty_pool")
  }

  test("WalletsApi#Get/Post wallet(s) from non existing pool") {
    val expectedErr = ErrorResponseBody(ErrorCode.Bad_Request, Map("response"->"Wallet pool doesn't exist", "pool_name"->"not_existing_pool"))
    val result = assertGetWallets("not_existing_pool", 0, 2, Status.BadRequest)
    val getWalletsErr = server.mapper.objectMapper.readValue[ErrorResponseBody](result.contentString)
    assert(getWalletsErr === expectedErr)
    val getWalletErr = server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertGetWallet("not_existing_pool", "my_wallet", Status.BadRequest).contentString)
    assert(getWalletErr === expectedErr)
    val postWalletErr = server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertWalletCreation("not_existing_pool", "my_wallet", "bitcoin", Status.BadRequest).contentString)
    assert(postWalletErr === expectedErr)
  }

  test("WalletsApi#Post wallet with non exist currency to existing pool") {
    createPool("random_pool")
    val expectedErr = ErrorResponseBody(ErrorCode.Bad_Request, Map("response"->"Currency not support", "currency_name"->"non_existing_currency"))
    val result = assertWalletCreation("random_pool", "my_wallet", "non_existing_currency", Status.BadRequest)
    val postWalletErr = server.mapper.objectMapper.readValue[ErrorResponseBody](result.contentString)
    assert(expectedErr === postWalletErr)
    deletePool("random_pool")
  }

  private def walletFromResponse(response: Response): WalletView = parse[WalletView](response)
  private def walletsFromResponse(response: Response): WalletsViewWithCount = parse[WalletsViewWithCount](response)

  private val WALLET_POOL = "wallet_pool"

  private def assertGetWallet(poolName: String, walletName: String, expected: Status): Response = {
    server.httpGet(path = s"/pools/$poolName/wallets/$walletName", headers = defaultHeaders, andExpect = expected)
  }

  private def assertGetWallets(poolName: String, offset: Int, count: Int, expected: Status): Response = {
    server.httpGet(path = s"/pools/$poolName/wallets?offset=$offset&count=$count", headers = defaultHeaders, andExpect = expected)
  }
}
