object Version {
  val akka             = "2.4.19"
  val akkaHttp         = "10.0.10"
  val akkaHttpPlayJson = "1.18.1"
  val cats             = "1.0.0"
  val commonsEmail     = "1.5"
  val jasypt           = "1.9.2"
  val jwt              = "0.12.1"
  val logback          = "1.2.3"
  val playJson         = "2.6.7"
  val pgDriver         = "42.1.4"
  val rapture          = "2.0.0-M9"
  val slick            = "3.2.1"
  val slick_pg         = "0.15.4"
  val scalaTest        = "3.0.4"
  val scopt            = "3.7.0"
  val typesafeConfig   = "1.3.1"
}

object Libraries {
  import sbt._

  val akkaHttpBundle = Seq(
    "com.typesafe.akka" %% "akka-http"           % Version.akkaHttp,
    "com.typesafe.akka" %% "akka-slf4j"          % Version.akka,
    "com.typesafe.play" %% "play-json"           % Version.playJson,
    "de.heikoseeberger" %% "akka-http-play-json" % Version.akkaHttpPlayJson,
    "ch.qos.logback"    % "logback-classic"      % Version.logback
  )

  val slickBundle = Seq(
    "com.typesafe.slick"  %% "slick"          % Version.slick,
    "com.typesafe.slick"  %% "slick-hikaricp" % Version.slick,
    "com.github.tminglei" %% "slick-pg"       % Version.slick_pg,
    "org.postgresql"      % "postgresql"      % Version.pgDriver
  )

  val akkaHttpTestkit = "com.typesafe.akka"  %% "akka-http-testkit" % Version.akkaHttp % "test"
  val cats            = "org.typelevel"      %% "cats-free"         % Version.cats
  val commonsEmail    = "org.apache.commons" % "commons-email"      % Version.commonsEmail
  val jasypt          = "org.jasypt"         % "jasypt"             % Version.jasypt
  val jwt             = "com.pauldijou"      %% "jwt-play-json"     % Version.jwt
  val `rapture-i18n`  = "com.propensive"     %% "rapture-i18n"      % Version.rapture
  val scalatest       = "org.scalatest"      %% "scalatest"         % Version.scalaTest % "test,it"
  val scopt           = "com.github.scopt"   %% "scopt"             % Version.scopt
  val typesafeConfig  = "com.typesafe"       % "config"             % Version.typesafeConfig

  val akkaHttpBundleWithTest = akkaHttpBundle ++ Seq(scalatest, akkaHttpTestkit)
}

