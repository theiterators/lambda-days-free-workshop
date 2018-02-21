name := "Forum"
organization := "pl.iterators"
version := "1.0"
scalaVersion := "2.12.4"
scalacOptions := Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-Xlint:_",
  "-Xfatal-warnings",
  "-Ypartial-unification",
  "-encoding",
  "utf8",
  "-target:jvm-1.8"
)

configs(IntegrationTest)
Defaults.itSettings

import Libraries._

libraryDependencies ++= akkaHttpBundleWithTest ++ slickBundle ++ Seq(cats, typesafeConfig, jasypt, jwt, commonsEmail, `rapture-i18n`, scopt)
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")

PostgresMigrations.settings

scalafmtVersion := "1.3.0"
scalafmtOnCompile := true

inConfig(IntegrationTest)(
  scalafmtSettings ++
    PostgresMigrations.itSettings ++ Seq(executeTests := (executeTests dependsOn flywayMigrate).value,
                                         flywayMigrate := (flywayMigrate dependsOn flywayClean).value))

enablePlugins(SbtTwirl)
TwirlKeys.templateImports := Seq("_root_.play.twirl.api.Html")
