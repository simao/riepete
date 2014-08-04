package io.simao.riepete.messages

import io.simao.riepete.parser.Metric

case class StatsdMetric(payload: String)

case class RiepeteMetric(statsdMetric: Metric)

case class MultiRiepeteMetric(metrics: Seq[RiepeteMetric])

