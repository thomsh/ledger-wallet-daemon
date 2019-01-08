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
mappings in (Compile, packageDoc) := Seq()

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

// Inspired by https://tpolecat.github.io/2017/04/25/scalac-flags.html
scalacOptions ++= Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
  "-language:higherKinds",             // Allow higher-kinded types
  "-language:implicitConversions",     // Allow definition of implicit functions called views
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
  "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
  "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",            // Option.apply used implicit view.
  "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match",              // Pattern match may not be typesafe.
  "-Ypartial-unification",             // Enable partial unification in type constructor inference
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates"            // Warn if a private member is unused.
)

libraryDependencies ++= Seq(
  compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.3.1"),
  "com.github.ghik" %% "silencer-lib" % "1.3.1" % Provided
)
