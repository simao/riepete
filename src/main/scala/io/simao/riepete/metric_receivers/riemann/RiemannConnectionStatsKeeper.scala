package io.simao.riepete.metric_receivers.riemann

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging}
import io.simao.riepete.messages.Metric

import scala.collection.immutable
import scala.util.Try

case object GetResetIntervalStats

import com.codahale.metrics._

sealed trait ConnectionStat
case class Received(count: Long) extends ConnectionStat
case class Sent(count: Long) extends ConnectionStat
case class SentFinished(duration: Long) extends ConnectionStat
case class Acked(count: Long) extends ConnectionStat
case class Dropped(count: Long) extends ConnectionStat
case class Failed(metrics: Seq[Metric], cause: Throwable) extends ConnectionStat

class RiemannConnectionStatsKeeper extends Actor with ActorLogging {
  val metrics = new MetricRegistry()

  var intervalStats = immutable.Map[String, Long]()

  JmxReporter.forRegistry(metrics)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build().start()

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    resetInterval()
  }

  def receive: Receive = {
    case m: ConnectionStat => m match {
      case Received(count) =>
        statsIncrement("received", count)
      case Sent(count) =>
        statsIncrement("sent", count)
        metrics.meter("sentPerSecond").mark(count)
        metrics.histogram("sentSize").update(count)
      case SentFinished(duration) =>
        metrics.timer("sendTime").update(duration, TimeUnit.MILLISECONDS)
      case Acked(count) => statsIncrement("acked", count)
      case Failed(q, _) => statsIncrement("ioerror", q.size)
      case Dropped(count) => statsIncrement("dropped", count)
    }

    case GetResetIntervalStats =>
      sender() ! statsMap()
      resetInterval()
  }

  private def statsMap() = {
    Map("totalSent" → getCounter("sent"),
        "totalReceived" → getCounter("received"),
        "acked" → getCounter("acked"),
        "sendTime" → getTimer("sendTime"),
        "dropped" → getCounter("dropped"),
        "ioerrors" → getCounter("ioerror"),
        "intervalAcked" → intervalStats("acked"),
        "intervalSent" → intervalStats("sent"),
        "intervalIoError" → intervalStats("ioerror"),
        "sent/sec" → getMeter("sentPerSecond")
        )
  }

  private def getMeter(key: String): String = {
    Try(metrics.getMeters.get(key)) map { m ⇒
      val rate = m.getOneMinuteRate / 60
      f"$rate%2.2f"
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

  private def resetInterval() = {
    intervalStats = immutable.Map("acked" → 0l, "sent" → 0l, "ioerror" → 0l)
  }

  private def statsIncrement(key: String, inc: Long = 1) = {
    metrics.counter(key).inc(inc)
    val c = intervalStats.getOrElse(key, 0l)
    intervalStats = intervalStats.updated(key, c + inc)
  }
}
