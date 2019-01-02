name := "daemon"

organization := "co.ledger"
scalaVersion := "2.12.2"

enablePlugins(JavaServerAppPackaging)
addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17")
concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)
parallelExecution in Test := false
parallelExecution in IntegrationTest := false
testForkedParallel in Test := false
testForkedParallel in IntegrationTest := false
testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a"))
mainClass in Compile := Some("co.ledger.wallet.daemon.Server")
test in assembly := {}

lazy val versions = new {
  val andrebeat = "0.4.0"
  val bitcoinj  = "0.14.4"
  val finatra   = "2.12.0"
  val guice     = "4.0"
  val h2        = "1.4.192"
  val logback   = "1.1.7"
  val postgre   = "9.3-1100-jdbc4"
  val slick     = "3.2.1"
  val sqlite    = "3.7.15-M1"
  val cats = "1.5.0-RC1"
}

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % versions.cats,

  "com.typesafe.slick"  %% "slick"              % versions.slick,
  "com.typesafe.slick"  %% "slick-hikaricp"     % versions.slick,
  "org.postgresql"      %  "postgresql"         % versions.postgre,
  "org.xerial"          %  "sqlite-jdbc"        % versions.sqlite,
  "com.h2database"      %  "h2"                 % versions.h2,

  "ch.qos.logback"      %  "logback-classic"    % versions.logback,
  "org.bitcoinj"        %  "bitcoinj-core"      % versions.bitcoinj,
  "io.github.andrebeat" %% "scala-pool"         % versions.andrebeat,

  "javax.websocket"             % "javax.websocket-api"     % "1.1"   % "provided",
  "org.glassfish.tyrus.bundles" % "tyrus-standalone-client" % "1.13.1",

  "com.twitter" %% "finatra-http"     % versions.finatra,
  "com.twitter" %% "finatra-jackson"  % versions.finatra,

  "com.twitter" %% "finatra-http"     % versions.finatra            % "test",
  "com.twitter" %% "finatra-jackson"  % versions.finatra            % "test",
  "com.twitter" %% "inject-server"    % versions.finatra            % "test",
  "com.twitter" %% "inject-app"       % versions.finatra            % "test",
  "com.twitter" %% "inject-core"      % versions.finatra            % "test",
  "com.twitter" %% "inject-modules"   % versions.finatra            % "test",

  "com.google.inject.extensions" % "guice-testlib" % versions.guice % "test",

  "com.twitter" %% "finatra-http"     % versions.finatra % "test" classifier "tests",
  "com.twitter" %% "finatra-jackson"  % versions.finatra % "test" classifier "tests",
  "com.twitter" %% "inject-server"    % versions.finatra % "test" classifier "tests",
  "com.twitter" %% "inject-app"       % versions.finatra % "test" classifier "tests",
  "com.twitter" %% "inject-core"      % versions.finatra % "test" classifier "tests",
  "com.twitter" %% "inject-modules"   % versions.finatra % "test" classifier "tests",


  "org.scalacheck"  %% "scalacheck"       % "1.13.4"  % "test",
  "org.scalatest"   %% "scalatest"        %  "3.0.0"  % "test",
  "org.specs2"      %% "specs2-mock"      % "2.4.17"  % "test",
  "junit"           %  "junit"            % "4.12"    % "test",
  "com.novocode"    %  "junit-interface"  % "0.11"    % "test",
  "org.mockito"     %  "mockito-core"     % "1.9.5"   % "test"
)
