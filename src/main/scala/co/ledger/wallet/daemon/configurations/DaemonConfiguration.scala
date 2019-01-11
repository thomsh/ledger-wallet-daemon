package co.ledger.wallet.daemon.configurations

import java.util.Locale

import com.typesafe.config.ConfigFactory
import slick.jdbc.JdbcProfile

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try

object DaemonConfiguration {
  private val config = ConfigFactory.load()
  private val PERMISSION_CREATE_USER: Int = 0x01
  private val DEFAULT_AUTH_TOKEN_DURATION: Int = 30000 // 30 seconds
  private val DEFAULT_SYNC_INTERVAL: Int = 24 // 24 hours
  private val DEFAULT_SYNC_INITIAL_DELAY: Int = 300 // 5 minutes

  val proxy: Option[Proxy] = {
    if (config.getBoolean("proxy.enabled")) {
      Some(Proxy(config.getString("proxy.host"), config.getInt("proxy.port")))
    } else {
      None
    }
  }

  val adminUsers: Seq[(String, String)] = if (config.hasPath("demo_users")) {
    val usersConfig = config.getConfigList("demo_users").asScala
    for {
      userConfig <- usersConfig
    } yield (userConfig.getString("username"), userConfig.getString("password"))

  } else { List[(String, String)]() }

  val whiteListUsers: Seq[(String, Int)] = if (config.hasPath("whitelist")){
    val usersConfig = config.getConfigList("whitelist")
    val users = new ListBuffer[(String, Int)]()
    for (i <- 0 until usersConfig.size()) {
      val userConfig = usersConfig.get(i)
      val pubKey = userConfig.getString("key").toUpperCase(Locale.US)
      val permissions = if (Try(userConfig.getBoolean("account_creation")).getOrElse(false)) PERMISSION_CREATE_USER else 0
      users += ((pubKey, permissions))
    }
    users.toList
  } else { List[(String, Int)]() }

  val authTokenDuration: Int =
    if (config.hasPath("authentication.token_duration")) { config.getInt("authentication.token_duration_in_seconds") * 1000 }
    else { DEFAULT_AUTH_TOKEN_DURATION }

  val dbProfileName: String = Try(config.getString("database_engine")).toOption.getOrElse("sqlite3")

  val dbProfile: JdbcProfile = dbProfileName match {
    case "sqlite3" =>
      slick.jdbc.SQLiteProfile
    case "postgres" =>
      slick.jdbc.PostgresProfile
    case "h2mem1" =>
      slick.jdbc.H2Profile
    case others => throw new Exception(s"Unknown database backend $others")
  }

  val isWhiteListDisabled: Boolean = if (!config.hasPath("disable_whitelist")) false else config.getBoolean("disable_whitelist")

  val synchronizationInterval: (Int, Int) = (
    if (config.hasPath("synchronization.initial_delay_in_seconds")) { config.getInt("synchronization.initial_delay_in_seconds") }
    else { DEFAULT_SYNC_INITIAL_DELAY },
    if(config.hasPath("synchronization.interval_in_hours")) { config.getInt("synchronization.interval_in_hours") }
    else { DEFAULT_SYNC_INTERVAL })

  val realTimeObserverOn: Boolean =
    if (config.hasPath("realtimeobservation")) { config.getBoolean("realtimeobservation") }
    else { false }

  val isPrintCoreLibLogsEnabled: Boolean = config.hasPath("debug.print_core_logs") && config.getBoolean("debug.print_core_logs")

  lazy val coreDataPath: String = Try(config.getString("core_data_path")).getOrElse("./core_data")

  val explorer: ExplorerConfig = {
    val explorer = config.getConfig("explorer")
    val api = explorer.getConfig("api")
    val connectionPoolSize = api.getInt("connection_pool_size")
    val paths = api.getConfigList("paths").asScala.toList.map { path =>
      val currency = path.getString("currency")
      val host = path.getString("host")
      val port = path.getInt("port")
      currency -> PathConfig(host, port)
    }.toMap
    val ws = explorer.getObject("ws").unwrapped().asScala.toMap.mapValues(_.toString)
    ExplorerConfig(ApiConfig(connectionPoolSize, paths), ws)
  }

  case class ApiConfig(connectionPoolSize: Int, paths: Map[String, PathConfig])
  case class PathConfig(host: String, port: Int) {
    def filterPrefix: PathConfig = {
      PathConfig(host.replaceFirst(".+?://", ""), port)
    }
  }
  case class ExplorerConfig(api: ApiConfig, ws: Map[String, String])
  case class Proxy(host: String, port: Int)
}
