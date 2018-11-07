package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.wallet.daemon.models.coins.Bitcoin
import co.ledger.wallet.daemon.models.coins.Coin.NetworkParamsView
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._

class Currency(coreC: core.Currency) {

  val name: String = coreC.getName

  val family: core.WalletType = coreC.getWalletType

  lazy val currencyView: CurrencyView = CurrencyView(
    coreC.getName,
    family,
    coreC.getBip44CoinType,
    coreC.getPaymentUriScheme,
    coreC.getUnits.asScala.map(newUnitView),
    newNetworkParamsView(coreC, family)
  )

  def parseUnsignedTransaction(rawTx: Array[Byte]): core.BitcoinLikeTransaction = family match {
    case core.WalletType.BITCOIN => core.BitcoinLikeTransactionBuilder.parseRawUnsignedTransaction(coreC, rawTx)
    case _ => throw new UnsupportedOperationException(s"No parser found for currency family '$family'")
  }


  def validateAddress(address: String): Boolean = core.Address.isValid(address, coreC)

  def convertAmount(amount: Long): core.Amount = core.Amount.fromLong(coreC, amount)

  private def newUnitView(coreUnit: core.CurrencyUnit): UnitView =
    UnitView(coreUnit.getName, coreUnit.getSymbol, coreUnit.getCode, coreUnit.getNumberOfDecimal)

  private def newNetworkParamsView(coreCurrency: core.Currency, currencyFamily: core.WalletType): NetworkParamsView = currencyFamily match {
    case core.WalletType.BITCOIN => Bitcoin.newNetworkParamsView(coreCurrency.getBitcoinLikeNetworkParameters)
    case _ => throw new UnsupportedOperationException
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Currency => that.isInstanceOf[Currency] && this.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    this.name.hashCode + this.family.hashCode()
  }

  override def toString: String = s"Currency(name: $name, family: $family)"
}

object Currency {
  def newInstance(coreC: core.Currency): Currency = {
    new Currency(coreC)
  }
}

case class CurrencyView(
                         @JsonProperty("name") name: String,
                         @JsonProperty("family") family: core.WalletType,
                         @JsonProperty("bip_44_coin_type") bip44CoinType: Int,
                         @JsonProperty("payment_uri_scheme") paymentUriScheme: String,
                         @JsonProperty("units") units: Seq[UnitView],
                         @JsonProperty("network_params") networkParams: NetworkParamsView
                       )

case class UnitView(
                     @JsonProperty("name") name: String,
                     @JsonProperty("symbol") symbol: String,
                     @JsonProperty("code") code: String,
                     @JsonProperty("magnitude") magnitude: Int
                   )