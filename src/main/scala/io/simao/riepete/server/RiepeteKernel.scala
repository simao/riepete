package io.simao.riepete.server

import akka.actor.ActorSystem
import akka.kernel.Bootable
import org.slf4j.LoggerFactory

import scala.io.Source

class RiepeteKernel extends Bootable {
  val system = ActorSystem("riepeteActorSystem")
  val log = LoggerFactory.getLogger(this.getClass.getName)

  def confFile = {
    Option(getClass.getClassLoader.getResource("riepete.conf"))
  }

  def startup() = {
    implicit val riemann_config =
      confFile
        .map(Source.fromURL)
        .map(Config(_))
        .getOrElse({
          log.warn("Could not load riepete.conf, using default config")
          Config.default
      })

    system.actorOf(RiepeteServer.props(), "RiepeteServer")
  }

  def shutdown() = {
    system.shutdown()
  }
}
