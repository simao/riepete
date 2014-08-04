package io.simao.riepete.metric_receivers

import akka.actor.{Props, ActorLogging, Actor}
import akka.event.Logging
import io.simao.riepete.messages.{MultiRiepeteMetric, RiepeteMetric}

object ConsoleReceiver {
  def props() = Props[ConsoleReceiver]
}

class ConsoleReceiver extends Actor with ActorLogging {
  def receive = {
    case MultiRiepeteMetric(metrics) =>
      for(m <- metrics)
        log.debug(m.toString)
  }
}
