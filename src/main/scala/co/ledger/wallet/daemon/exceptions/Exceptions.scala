package co.ledger.wallet.daemon.exceptions

import java.util.UUID

case class ERC20NotFoundException(contract: String) extends DaemonException(s"No ERC20 token $contract in your account")

case class ERC20BalanceNotEnough(tokenAddress: String, balance: Long, need: Long)
  extends DaemonException(s"Not enough funds on ERC20 ($tokenAddress) account: having $balance, need $need")

case class AccountNotFoundException(accountIndex: Int) extends DaemonException(s"Account with index $accountIndex doesn't exist")

case class OperationNotFoundException(cursor: UUID) extends DaemonException(s"Operation with previous or next cursor $cursor doesn't exist")

case class WalletNotFoundException(walletName: String) extends DaemonException(s"Wallet $walletName doesn't exist")

case class WalletPoolNotFoundException(poolName: String) extends DaemonException(s"Wallet pool $poolName doesn't exist")

case class WalletPoolAlreadyExistException(poolName: String) extends DaemonException(s"Wallet pool $poolName already exists")

case class CurrencyNotFoundException(currencyName: String) extends DaemonException(s"Currency $currencyName is not supported")

case class UserNotFoundException(pubKey: String) extends DaemonException(s"User $pubKey doesn't exist")

case class UserAlreadyExistException(pubKey: String) extends DaemonException(s"User $pubKey already exists")

case class CoreBadRequestException(msg: String, t: Throwable) extends DaemonException(msg)

case class DaemonDatabaseException(msg: String, e: Throwable) extends Exception(msg, e)

class DaemonException(msg: String) extends Exception(msg)

case class SignatureSizeUnmatchException(txSize: Int, signatureSize: Int) extends DaemonException("Signatures and transaction inputs size not matching")
