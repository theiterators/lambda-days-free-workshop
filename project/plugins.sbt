addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.12")

addSbtPlugin("org.flywaydb" % "flyway-sbt" % "4.2.0")
resolvers += "Flyway" at "https://flywaydb.org/repo"

addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.3.4")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")