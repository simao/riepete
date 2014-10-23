package io.simao.riepete.metric_receivers

import akka.actor.{Actor, ActorLogging, Props}
import io.simao.riepete.messages.MetricSeq

object ConsoleReceiver {
  def props() = Props[ConsoleReceiver]
}

class ConsoleReceiver extends Actor with ActorLogging {
  def receive = {
    case MetricSeq(metrics) =>
      for(m <- metrics)
        log.debug(m.toString)
  }
}
