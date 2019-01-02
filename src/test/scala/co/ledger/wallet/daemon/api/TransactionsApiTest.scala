package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}

/**
  * Test cases for [[co.ledger.wallet.daemon.controllers.TransactionsController]].
  *
  * User: Ting Tu
  * Date: 25-04-2018
  * Time: 11:26
  *
  */
class TransactionsApiTest extends APIFeatureTest {

  test("TransactionsApi#Create and sign transaction") {
    val poolName = "transactionsAPI1"
    createPool(poolName)
    assertWalletCreation(poolName, "wallet", "bitcoin", Status.Ok)
    assertCreateAccount(ACCOUNT_BODY, poolName, "wallet", Status.Ok)
    assertSyncPool(Status.Ok)
    assertCreateTransaction(TX_BODY_WITH_EXCLUDE_UTXO, poolName, "wallet", 0, Status.Ok)
    assertCreateTransaction(TX_BODY, poolName, "wallet", 0, Status.BadRequest)
    assertCreateTransaction(INVALID_FEE_LEVEL_BODY, poolName, "wallet", 0, Status.BadRequest)
    assertSignTransaction(TX_MISSING_APPENDED_SIG, poolName, "wallet", 0, Status.BadRequest)
    assertSignTransaction(TX_MISSING_ONE_SIG, poolName, "wallet", 0, Status.BadRequest)
    assertSignTransaction(TX_TO_SIGN_BODY, poolName, "wallet", 0, Status.InternalServerError)
  }

  test("AccountsApi#Broadcast signed transaction") {
    val poolName = "ledger"
    createPool(poolName)
    assertWalletCreation(poolName, "bitcoin_testnet", "bitcoin_testnet", Status.Ok)
    assertCreateAccount(ACCOUNT_BODY, poolName, "bitcoin_testnet", Status.Ok)
    assertSyncPool(Status.Ok)
    assertSignTransaction(TESTNET_TX_TO_SIGN_BODY, poolName, "bitcoin_testnet", 0, Status.InternalServerError)
    assertGetAccount(poolName, "bitcoin_testnet", 0, Status.Ok)
  }

  private def assertSignTransaction(tx: String, poolName: String, walletName: String, accountIndex: Int, expected: Status): Response = {
    server.httpPost(
      s"/pools/$poolName/wallets/$walletName/accounts/$accountIndex/transactions/sign",
      tx,
      headers = defaultHeaders,
      andExpect = expected
    )
  }

  private def assertGetAccount(poolName: String, walletName: String, accountIndex: Int, expected: Status): Response = {
    server.httpGet(
      s"/pools/$poolName/wallets/$walletName/accounts/$accountIndex",
      headers = defaultHeaders,
      andExpect = expected
    )
  }

  private def assertCreateTransaction(tx: String, poolName: String, walletName: String, accountIndex: Int, expected: Status): Response = {
    server.httpPost(
      s"/pools/$poolName/wallets/$walletName/accounts/$accountIndex/transactions",
      tx,
      headers = defaultHeaders,
      andExpect = expected
    )
  }

  private val TX_TO_SIGN_BODY =
    """{
       |"raw_transaction": "0100000002A91F09D74BEE55D8E9F3673E42102FA9AB71185C47E83229076452C44EC301E9000000001976A91455F719785040EC522FB6CF9C4B45A7011912529188ACFFFFFFFFEA1B6F36DA6745A399878AB4B67BE9443A6155123BA1EFD252823F6987671095000000001976A914261E04A99A3E387DBA09A667F73C74E8C5A2523088ACFFFFFFFF02002D31010000000017A914394D7CE052572BF35DFC32CD6EFF5B4BE6D9300B870AC5E203000000001976A914F3CEB507BD0D264CE8B4C9564EA63E9426B3B66B88AC49EF0700",
       |"signatures": ["0100000002A91F0", "100000002A91F"],
       |"pubkeys": ["033B811F166EA0E8D764530960047A398F50AB89B40E70537DB06C303C7939930F","0229355FB9801567F6C332978F1383D7B6E717B7A3991524BC95F9D6A743DCA6CD"]
       |}""".stripMargin

  private val TX_MISSING_ONE_SIG =
    """{
       |"raw_transaction": "0100000002A91F09D74BEE55D8E9F3673E42102FA9AB71185C47E83229076452C44EC301E9000000001976A91455F719785040EC522FB6CF9C4B45A7011912529188ACFFFFFFFFEA1B6F36DA6745A399878AB4B67BE9443A6155123BA1EFD252823F6987671095000000001976A914261E04A99A3E387DBA09A667F73C74E8C5A2523088ACFFFFFFFF02002D31010000000017A914394D7CE052572BF35DFC32CD6EFF5B4BE6D9300B870AC5E203000000001976A914F3CEB507BD0D264CE8B4C9564EA63E9426B3B66B88AC49EF0700",
       |"signatures": ["0100000002A91F0"],
       |"pubkeys": ["033B811F166EA0E8D764530960047A398F50AB89B40E70537DB06C303C7939930F","0229355FB9801567F6C332978F1383D7B6E717B7A3991524BC95F9D6A743DCA6CD"]
       |}""".stripMargin

  private val TX_MISSING_APPENDED_SIG =
    """{
       |"raw_transaction": "0100000002A91F09D74BEE55D8E9F3673E42102FA9AB71185C47E83229076452C44EC301E9000000001976A91455F719785040EC522FB6CF9C4B45A7011912529188ACFFFFFFFFEA1B6F36DA6745A399878AB4B67BE9443A6155123BA1EFD252823F6987671095000000001976A914261E04A99A3E387DBA09A667F73C74E8C5A2523088ACFFFFFFFF02002D31010000000017A914394D7CE052572BF35DFC32CD6EFF5B4BE6D9300B870AC5E203000000001976A914F3CEB507BD0D264CE8B4C9564EA63E9426B3B66B88AC49EF0700",
       |"signatures": ["0100000002A91F0"],
       |"pubkeys": ["033B811F166EA0E8D764530960047A398F50AB89B40E70537DB06C303C7939930F"]
       |}""".stripMargin

  private val INVALID_FEE_LEVEL_BODY =
    """{""" +
      """"recipient": "36v1GRar68bBEyvGxi9RQvdP6Rgvdwn2C2",""" +
      """"fees_level": "OTHER",""" +
      """"amount": 10000""" +
      """}"""

  private val TX_BODY =
    """{""" +
      """"recipient": "36v1GRar68bBEyvGxi9RQvdP6Rgvdwn2C2",""" +
      """"fees_per_byte": 397000,""" +
      """"fees_level": "FAST",""" +
      """"amount": 10000""" +
      """}"""

  private val TX_BODY_WITH_EXCLUDE_UTXO =
    """{""" +
    """"recipient": "36v1GRar68bBEyvGxi9RQvdP6Rgvdwn2C2",""" +
    """"fees_level": "NORMAL",""" +
    """"amount": 20000000,""" +
    """"exclude_utxos":{"beabf89d72eccdcb895373096a402ae48930aa54d2b9e4d01a05e8f068e9ea49": 0 }""" +
    """}"""
  private val ACCOUNT_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'/0'",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""

  private val TESTNET_TX_TO_SIGN_BODY =
    """{
       |"raw_transaction": "0100000001531ABD78576139559EA37E48A6554714D7434AE2BDF1451076A4C98219762AF20000000000FFFFFFFF02E8030000000000001976A9147F5365AABF5001DC5A3A21246E639B2C1FAD804888ACF4A03D00000000001976A914F9E27FEF11F7CE2F3EBE80535DD5AC812A85CCDD88AC8BC71300",
       |"signatures": ["3045022100DD6BA1732C7BD0E94F9FE71B6290E04A4A4B293B949FC1C585A0382EEADD62A0022072BFC3F077652C9B7EFC1C92969E00438AC0C7296CBA0A4B97AF08C9DAC36B74"],
       |"pubkeys": ["02A1DED78DAD86FE76E2238E29C58B549FBA769EF2601EBA643D96B17D3C6C4D4E"]
       |}""".stripMargin

}
