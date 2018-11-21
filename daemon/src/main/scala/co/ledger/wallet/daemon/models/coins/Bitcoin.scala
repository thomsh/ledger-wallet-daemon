package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.models.coins.Coin._
import co.ledger.wallet.daemon.utils.{HexUtils, Utils}
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object Bitcoin {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  val currencyFamily = core.WalletType.BITCOIN

  def newNetworkParamsView(from: core.BitcoinLikeNetworkParameters): NetworkParamsView = {
    BitcoinNetworkParamsView(
      from.getIdentifier,
      HexUtils.valueOf(from.getP2PKHVersion),
      HexUtils.valueOf(from.getP2SHVersion),
      HexUtils.valueOf(from.getXPUBVersion),
      from.getFeePolicy.name,
      from.getDustAmount,
      from.getMessagePrefix,
      from.getUsesTimestampedTransaction
    )
  }

  def newTransactionView(from: core.BitcoinLikeTransaction): TransactionView = {
    BitcoinTransactionView(
      Option(from.getBlock).map (newBlockView),
      Option(from.getFees).map (fees => fees.toLong),
      from.getHash,
      from.getTime,
      from.getInputs.asScala.map(newInputView),
      from.getLockTime,
      from.getOutputs.asScala.map(newOutputView)
    )
  }

  def newUnsignedTransactionView(from: core.BitcoinLikeTransaction, feesPerByte: Long): Future[UnsignedBitcoinTransactionView] = {
    Future.sequence(from.getInputs.asScala.map(newUnsignedInputView)).map { unsignedInputs =>
      UnsignedBitcoinTransactionView(
        newEstimatedSizeView(from.getEstimatedSize),
        from.getFees.toLong,
        feesPerByte,
        unsignedInputs,
        from.getLockTime,
        from.getOutputs.asScala.map(newUnsignedOutputView),
        HexUtils.valueOf(from.serialize())
      )
    }
  }

  private def newUnsignedOutputView(from: core.BitcoinLikeOutput): UnsignedBitcoinOutputView = {
    UnsignedBitcoinOutputView(
      from.getAddress,
      from.getValue.toLong,
      HexUtils.valueOf(from.getScript),
      Option(from.getDerivationPath).map(_.toString)
    )
  }

  private def newEstimatedSizeView(from: core.EstimatedSize): EstimatedSizeView = {
    EstimatedSizeView(from.getMax, from.getMin)
  }

  private def newBlockView(from: core.BitcoinLikeBlock): BlockView = {
    BitcoinBlockView(from.getHash, from.getHeight, from.getTime)
  }

  private def newUnsignedInputView(from: core.BitcoinLikeInput): Future[UnsignedBitcoinInputView] = {
    from.getPreviousTransaction() map { previousTx =>
      val derivationPath = for {
        path <- from.getDerivationPath.asScala
        pubKey <- from.getPublicKeys.asScala
      } yield (path.toString, HexUtils.valueOf(pubKey))
      UnsignedBitcoinInputView(
        from.getAddress,
        from.getValue.toLong,
        from.getPreviousTxHash,
        HexUtils.valueOf(previousTx),
        from.getPreviousOutputIndex,
        derivationPath.toMap)
    }
  }

  private def newInputView(from: core.BitcoinLikeInput): InputView = {

    BitcoinInputView(from.getAddress, from.getValue.toLong, Option(from.getCoinbase), Option(from.getPreviousTxHash), Option(from.getPreviousOutputIndex))
  }

  private def newOutputView(from: core.BitcoinLikeOutput): OutputView = {
    BitcoinOutputView(from.getAddress, from.getTransactionHash, from.getOutputIndex, from.getValue.toLong, HexUtils.valueOf(from.getScript))
  }
}

case class BitcoinNetworkParamsView(
                                     @JsonProperty("identifier") identifier: String,
                                     @JsonProperty("p2pkh_version") p2pkhVersion: String,
                                     @JsonProperty("p2sh_version") p2shVersion: String,
                                     @JsonProperty("xpub_version") xpubVersion: String,
                                     @JsonProperty("fee_policy") feePolicy: String,
                                     @JsonProperty("dust_amount") dustAmount: Long,
                                     @JsonProperty("message_prefix") messagePrefix: String,
                                     @JsonProperty("uses_timestamped_transaction") usesTimeStampedTransaction: Boolean
                                   ) extends NetworkParamsView

case class BitcoinTransactionView(
                                   @JsonProperty("block") block: Option[BlockView],
                                   @JsonProperty("fees") fees: Option[Long],
                                   @JsonProperty("hash") hash: String,
                                   @JsonProperty("time") time: Date,
                                   @JsonProperty("inputs") inputs: Seq[InputView],
                                   @JsonProperty("lock_time") lockTime: Long,
                                   @JsonProperty("outputs") outputs: Seq[OutputView]
                                 ) extends TransactionView

case class BitcoinBlockView(
                           @JsonProperty("hash") hash: String,
                           @JsonProperty("height") height: Long,
                           @JsonProperty("time") time: Date
                           ) extends BlockView

case class BitcoinInputView(
                           @JsonProperty("address") address: String,
                           @JsonProperty("value") value: Long,
                           @JsonProperty("coinbase") coinbase: Option[String],
                           @JsonProperty("previous_transaction_hash") previousTxHash: Option[String],
                           @JsonProperty("previous_output_index") previousOutputIndex: Option[Int]
                           ) extends InputView

case class BitcoinOutputView(
                            @JsonProperty("address") address: String,
                            @JsonProperty("transaction_hash") txHash: String,
                            @JsonProperty("output_index") outputIndex: Int,
                            @JsonProperty("value") value: Long,
                            @JsonProperty("script") script: String
                            ) extends OutputView

case class UnsignedBitcoinInputView(
                                   @JsonProperty("address") address: String,
                                   @JsonProperty("value") value: Long,
                                   @JsonProperty("previous_transaction_hash") previousTxHash: String,
                                   @JsonProperty("previous_transaction_raw") previousTx: String,
                                   @JsonProperty("previous_output_index") previousOutputIndex: Int,
                                   @JsonProperty("derivation_paths") derivationPaths: Map[String, String]
                                   ) extends InputView

case class UnsignedBitcoinOutputView(
                                    @JsonProperty("address") address: String,
                                    @JsonProperty("value") value: Long,
                                    @JsonProperty("script") script: String,
                                    @JsonProperty("derivation_path") derivationPath: Option[String]
                                    ) extends OutputView

case class UnsignedBitcoinTransactionView(
                            @JsonProperty("estimated_size") estimatedSize: EstimatedSizeView,
                            @JsonProperty("fees") fees: Long,
                            @JsonProperty("fees_per_byte") feesPerByte: Long,
                            @JsonProperty("inputs") inputs: Seq[UnsignedBitcoinInputView],
                            @JsonProperty("lock_time") lockTime: Long,
                            @JsonProperty("outputs") outputs: Seq[UnsignedBitcoinOutputView],
                            @JsonProperty("raw_transaction") rawTransaction: String) extends TransactionView

case class EstimatedSizeView(@JsonProperty("max") max: Int, @JsonProperty("min") min: Int)
