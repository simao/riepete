import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{Dist, distJvmOptions, distMainClass, outputDirectory}
import sbt.Keys._
import sbt._

object RiepeteKernelBuild extends Build {
  val Organization = "io.simao"
  val Version      = "0.0.2"
  val ScalaVersion = "2.11.4"
  val Name = "riepete"

  lazy val RiepeteKernel = Project(
    id = "riepete-kernel",
    base = file("."),
    settings = defaultSettings ++ AkkaKernelPlugin.distSettings ++ Seq(
      libraryDependencies ++= Dependencies.all,
      distJvmOptions in Dist := "-Xms256M -Xmx1024M",
      outputDirectory in Dist := file("target/riepete-dist"),
      distMainClass in Dist := "akka.kernel.Main io.simao.riepete.server.RiepeteKernel"
    )
  )

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := Organization,
    version      := Version,
    scalaVersion := ScalaVersion,
    name         := Name,
    crossPaths   := false,
    organizationName := "simao.io",
    organizationHomepage := Some(url("https://simao.io"))
  )

  lazy val defaultSettings = buildSettings ++ Seq(
    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),

    resolvers += "clojars" at "http://clojars.org/repo/"
  )
}

object Dependencies {
  object Versions {
    val Akka      = "2.3.6"
  }

  val all = List(
    "com.typesafe.akka" %% "akka-kernel" % Versions.Akka,
    "com.typesafe.akka" %% "akka-slf4j"  % Versions.Akka,
    "com.typesafe.akka" %% "akka-actor" % Versions.Akka,
    "com.typesafe.akka" %% "akka-testkit" % Versions.Akka % "test",

    "ch.qos.logback" % "logback-classic" % "1.1.2",
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1",
    "io.argonaut" %% "argonaut" % "6.0.4",
    "io.dropwizard.metrics" % "metrics-core" % "3.1.0",
    "com.aphyr" % "riemann-java-client" % "0.2.10",

    "org.scalatest" %% "scalatest" % "2.2.0" % "test",
    "org.scalacheck" %% "scalacheck" % "1.11.6" % "test"
  )
}
