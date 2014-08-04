name := "riepete"

version := "1.0"

scalaVersion := "2.11.1"

resolvers += "clojars" at "http://clojars.org/repo/"

libraryDependencies += "com.aphyr" % "riemann-java-client" % "0.2.10"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.3"

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.3.3"

libraryDependencies += "com.typesafe.akka" %% "akka-kernel" % "2.3.3"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2"

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1"

libraryDependencies += "io.argonaut" %% "argonaut" % "6.0.4"

libraryDependencies += "io.dropwizard.metrics" % "metrics-core" % "3.1.0"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.3.3" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.11.6" % "test"
