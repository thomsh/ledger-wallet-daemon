package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core._
import co.ledger.wallet.daemon.models.coins.Coin.{NetworkParamsView, TransactionView}
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty
import co.ledger.wallet.daemon.utils.Utils.RichBigInt

import scala.collection.JavaConverters._

case class EthereumNetworkParamView(
                                     @JsonProperty("identifier") identifier: String,
                                     @JsonProperty("message_prefix") messagePrefix: String,
                                     @JsonProperty("xpub_version") xpubVersion: String,
                                     @JsonProperty("additional_eips") additionalEIPs: List[String],
                                     @JsonProperty("timestamp_delay") timestampDelay: Long
                                   ) extends NetworkParamsView

object EthereumNetworkParamView {
  def apply(n: EthereumLikeNetworkParameters): EthereumNetworkParamView =
    EthereumNetworkParamView(
      n.getIdentifier,
      n.getMessagePrefix,
      HexUtils.valueOf(n.getXPUBVersion),
      n.getAdditionalEIPs.asScala.toList,
      n.getTimestampDelay)
}

case class EthereumTransactionView(
                                    @JsonProperty("block") block: Option[CommonBlockView],
                                    @JsonProperty("hash") hash: String,
                                    @JsonProperty("receiver") receiver: String,
                                    @JsonProperty("sender") sender: String,
                                    @JsonProperty("value") value: scala.BigInt,
                                    @JsonProperty("erc20") erc20: Option[EthereumTransactionView.ERC20],
                                    @JsonProperty("gas_price") gasPrice: scala.BigInt,
                                    @JsonProperty("gas_limit") gasLimit: scala.BigInt,
                                    @JsonProperty("date") date: Date
                                  ) extends TransactionView

object EthereumTransactionView {
  def apply(tx: EthereumLikeTransaction): EthereumTransactionView = {
    EthereumTransactionView(
      Option(tx.getBlock).map(CommonBlockView.apply),
      tx.getHash,
      tx.getReceiver.toEIP55,
      tx.getSender.toEIP55,
      tx.getValue.toBigInt.asScala,
      ERC20.from(tx.getData).toOption,
      tx.getGasPrice.toBigInt.asScala,
      tx.getGasLimit.toBigInt.asScala,
      tx.getDate,
    )
  }

  case class ERC20(receiver: String, amount: scala.BigInt)

  object ERC20 {
    private val erc20 = raw"a9059cbb0{24}([0-9a-f]{40})([0-9a-f]{64})".r

    def from(byteArray: Array[Byte]): Either[String, ERC20] = {
      if(byteArray.length == 68) {
        val data = HexUtils.valueOf(byteArray).toLowerCase()
        data match {
          case erc20(receiver, amount) =>
            Right(ERC20(receiver, scala.BigInt(amount, 16)))
          case _ => Left(s"bad erc20 data format: $data")
        }
      } else Left("bad erc20 data size")
    }
  }

}

case class UnsignedEthereumTransactionView(
                                            @JsonProperty("hash") hash: String,
                                            @JsonProperty("receiver") receiver: String,
                                            @JsonProperty("value") value: scala.BigInt,
                                            @JsonProperty("gas_price") gasPrice: scala.BigInt,
                                            @JsonProperty("gas_limit") gasLimit: scala.BigInt,
                                            @JsonProperty("fees") fees: scala.BigInt,
                                            @JsonProperty("raw_transaction") rawTransaction: String
                                          ) extends TransactionView

object UnsignedEthereumTransactionView {
  def apply(tx: EthereumLikeTransaction): UnsignedEthereumTransactionView = {
    UnsignedEthereumTransactionView(
      tx.getHash,
      tx.getReceiver.toEIP55,
      tx.getValue.toBigInt.asScala,
      tx.getGasPrice.toBigInt.asScala,
      tx.getGasLimit.toBigInt.asScala,
      tx.getGasPrice.toBigInt.asScala * tx.getGasLimit.toBigInt.asScala,
      HexUtils.valueOf(tx.serialize())
    )
  }
}
