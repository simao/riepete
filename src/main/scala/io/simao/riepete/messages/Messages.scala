package io.simao.riepete.messages

import io.simao.riepete.parser.ParsedMetric

case class StatsdMetric(payload: String, hostname: String)

case class Metric(statsdMetric: ParsedMetric, host: String) {
  def this(statsdMetric: ParsedMetric) = this(statsdMetric, "unknown")
}

object Metric {
  def apply(statsdMetric: ParsedMetric) = new Metric(statsdMetric)
}

case class MetricSeq(metrics: Seq[Metric])

