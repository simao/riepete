package io.simao.riepete.metric_receivers

import akka.actor.{Actor, ActorLogging, Props}
import io.simao.riepete.messages.{MultiRiepeteMetric, RiepeteMetric, StatsdMetric}
import io.simao.riepete.parser.StatsParser
import io.simao.riepete.server.Config

import scala.util.{Failure, Success}

object StatsReceiver {
  def props()(implicit config: Config) = {
    Props(new StatsReceiver)
  }
}

class StatsReceiver(implicit config: Config) extends Actor with ActorLogging {
  lazy val receivers = List(
    context.actorOf(ConsoleReceiver.props(), "consoleReceiver"),
    context.actorOf(RiemannReceiverRouter.props(), "riemannReceiverRouter")
  )

  def receive: Receive = {
    case StatsdMetric(metric_repr) =>
      handleMetric(metric_repr)
  }

  def handleMetric(repr: String) = {
    StatsParser(repr) match {
      case Success(m) =>
        val metrics = m.map(RiepeteMetric)
        val multi_metric_msg = MultiRiepeteMetric(metrics)

        for(r <- receivers)
          r ! multi_metric_msg

      case Failure(ex) =>
        log.warning("Could not parse metric from client: " + ex.getLocalizedMessage)
    }
  }
}
