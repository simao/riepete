package io.simao.riepete.metric_receivers

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging}
import io.simao.riepete.messages.RiepeteMetric

import scala.util.Try

case object StatsRequest

import com.codahale.metrics._

sealed trait ConnectionStat
case class Sent(count: Long) extends ConnectionStat
case class SentFinished(duration: Long) extends ConnectionStat
case class Acked(count: Long) extends ConnectionStat
case class Dropped(count: Long) extends ConnectionStat
case class Failed(metrics: Seq[RiepeteMetric], cause: Throwable) extends ConnectionStat

class ConnectionStatsKeeper extends Actor with ActorLogging {
  val metrics = new MetricRegistry()

  JmxReporter.forRegistry(metrics)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build().start()

  def receive: Receive = {
    case m: ConnectionStat => m match {
      case Sent(count) =>
        statsIncrement("sent", count)
        metrics.histogram("sentH").update(count)
      case SentFinished(duration) =>
        metrics.timer("sendTime").update(duration, TimeUnit.MILLISECONDS)
      case Acked(count) => statsIncrement("acked", count)
      case Failed(q, _) => statsIncrement("error", q.size)
      case Dropped(count) => statsIncrement("dropped", count)
    }

    case StatsRequest =>
      sender() ! statsMap()
  }

  private def statsMap() = {
    Map("sent" -> getCounter("sent"),
        "sentH" -> getHistogram("sentH"),
        "acked" -> getCounter("acked"),
        "sendTime" -> getTimer("sendTime"),
        "errors" -> getCounter("errors"),
        "dropped" -> getCounter("dropped")
        )
  }

  private def getHistogram(key: String) = {
    Try(metrics.getHistograms.get(key)) map { h =>
      val snapshot = h.getSnapshot
      val mean = snapshot.getMean.toLong
      val stddev = snapshot.getStdDev
      f"$mean (σ=$stddev%2.2f)"
    } getOrElse "n/a"
  }

  private def getCounter(key: String) = {
    Try(metrics.getCounters.get(key).getCount).getOrElse(0)
  }

  private def getTimer(key: String) = {
      Try(metrics.getTimers.get(key)) map { m =>
        val snapshot = m.getSnapshot
        val mean = snapshot.getMean * 1.0e-6
        val stddev = snapshot.getStdDev  * 1.0e-6
        f"$mean%2.2fms (σ=$stddev%2.2f)"
      } getOrElse "n/a"
  }

  private def statsIncrement(key: String, inc: Long = 1) = {
    metrics.counter(key).inc(inc)
  }
}
