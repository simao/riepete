package io.simao.riepete.messages

import io.simao.riepete.parser.Metric

case class StatsdMetric(payload: String, hostname: String)

case class RiepeteMetric(statsdMetric: Metric, host: String) {
  def this(statsdMetric: Metric) = this(statsdMetric, "unknown")
}

object RiepeteMetric {
  def apply(statsdMetric: Metric) = new RiepeteMetric(statsdMetric)
}

case class MultiRiepeteMetric(metrics: Seq[RiepeteMetric])

