package co.ledger.wallet.daemon.libledger_core.filesystem

import java.io.File

import co.ledger.core.PathResolver

class ScalaPathResolver(identifier: String) extends PathResolver {
  /**
    * Resolves the path for a SQLite database file.
    *
    * @param path The path to resolve.
    * @return The resolved path.
    */
  override def resolveDatabasePath(path: String): String = resolve(path, "databases")


  /**
    * Resolves the path of a single log file.
    *
    * @param path The path to resolve.
    * @return The resolved path.
    */
  override def resolveLogFilePath(path: String): String = resolve(path, "logs")

  /**
    * Resolves the path for a json file.
    *
    * @param path The path to resolve.
    * @return The resolved path.
    */
  override def resolvePreferencesPath(path: String): String = resolve(path, "preferences")

  private def resolve(path: String, folder: String) = {
    val resolvedPath = new File(new File(installDirectory, folder), path)
    resolvedPath.getParentFile.mkdirs()
    println(s"Path ${resolvedPath.getAbsolutePath}")
    resolvedPath.getAbsolutePath
  }
  lazy val installDirectory: File = {
    import java.net.URLDecoder
    val jarPath = URLDecoder.decode(classOf[Nothing].getProtectionDomain.getCodeSource.getLocation.getPath, "UTF-8")
    new File(new File(jarPath).getParentFile, identifier)
  }
}
