package io.simao.riepete.server

import akka.actor.{Actor, ActorLogging, Props}
import io.simao.riepete.messages.{Metric, MetricSeq, StatsdMetric}
import io.simao.riepete.metric_receivers.ConsoleReceiver
import io.simao.riepete.metric_receivers.riemann.RiemannReceiverRouter
import io.simao.riepete.parser.{ParsedMetric, StatsParser}

import scala.util.{Failure, Success}

object StatsdMetricHandler {
  def props()(implicit config: Config) = {
    Props(new StatsdMetricHandler)
  }
}

class StatsdMetricHandler(implicit config: Config) extends Actor with ActorLogging {
  lazy val receivers = List(
    context.actorOf(ConsoleReceiver.props(), "consoleReceiver"),
    context.actorOf(RiemannReceiverRouter.props(), "riemannReceiverRouter")
  )

  def receive: Receive = {
    case StatsdMetric(metric_repr, hostname) =>
      handleMetric(metric_repr, hostname)
  }

  def handleMetric(repr: String, hostname: String) = {
    StatsParser[ParsedMetric](repr) match {
      case Success(m) =>
        val metrics = m.map(x => Metric(x, hostname))
        val multi_metric_msg = MetricSeq(metrics)

        for(r <- receivers)
          r ! multi_metric_msg

      case Failure(ex) =>
        log.warning("Could not parse metric from client: " + ex.getLocalizedMessage)
    }
  }
}
