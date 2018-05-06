package co.ledger.wallet.daemon.controllers.requests

import co.ledger.wallet.daemon.models.FeeMethod
import com.twitter.finatra.validation.ValidationResult

object CommonMethodValidations {

  def validateOptionalAccountIndex(accountIndex: Option[Int]): ValidationResult =
    ValidationResult.validate(accountIndex.isEmpty || accountIndex.get >= 0, "account_index: index can not be less than zero")

  def validateName(name: String, nameStr: String): ValidationResult = {
    ValidationResult.validate(
      REGEX.pattern.matcher(nameStr).matches,
      s"$name: invalid $name, $name should match ${REGEX.toString()}")
  }

  def validateFees(feeAmount: Option[Long], feeLevel: Option[String]): ValidationResult = {
    ValidationResult.validate(feeAmount.isDefined
      || (feeLevel.isDefined && FeeMethod.isValid(feeLevel.get)),
      "fee_amount or fee_level must be defined, fee_level must be one of 'FAST', 'NORMAL', 'SLOW'")
  }

  private val REGEX = "([0-9a-zA-Z]+[_]?)+[0-9a-zA-Z]+".r
}
