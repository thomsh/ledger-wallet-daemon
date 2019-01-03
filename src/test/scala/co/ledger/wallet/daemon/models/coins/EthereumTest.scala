package co.ledger.wallet.daemon.models.coins

import co.ledger.wallet.daemon.models.coins.EthereumTransactionView.ERC20
import co.ledger.wallet.daemon.utils.HexUtils
import org.scalatest.{FlatSpec, Matchers}

/**
  * Describe your class here.
  *
  * User: Chenyu LU
  * Date: 03-01-2019
  * Time: 16:39
  *
  */
class EthereumTest extends FlatSpec with Matchers {
  "ERC20 input" should "be properly parsed" in {
    val sampleInput = "a9059cbb000000000000000000000000eee00dd01ece2c5eac353c617718faaebc720b380000000000000000000000000000000000000000000000056bc75e2d63100000"
    val sampleBinary = HexUtils.valueOf(sampleInput)
    val correctToAddress = "eee00dd01ece2c5eac353c617718faaebc720b38"
    val correctAmount: BigInt = BigInt("100000000000000000000")
    val result = ERC20.from(sampleBinary)
    result shouldBe Right(ERC20(correctToAddress, correctAmount))
  }
}
