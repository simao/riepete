package io.simao.riepete.metric_receivers.riemann

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.TimeUnit

import akka.actor._
import com.aphyr.riemann.Proto.{Event, Msg}
import com.aphyr.riemann.client.RiemannClient
import io.simao.riepete.messages.{Metric, MetricSeq}
import io.simao.riepete.server.Config

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{Future, blocking}
import scala.language.postfixOps
import scala.util.Try

case object HeartBeatAlarm
case object Reconnect
case object Restarted

class AckNotReceivedException extends Exception
class PromiseTimedOutException extends Exception

object RiemannClientActor {
  def props()(implicit config: Config) = {
    Props(new RiemannClientActor)
  }
}

class RiemannClientActor(implicit config: Config) extends Actor with ActorLogging {
  import context.system
  implicit val executionContext = system.dispatchers.lookup("riemann-sender-dispatcher")

  val riemannReceiver = context.parent

  lazy val riemannClient = {
    val remote = new InetSocketAddress(config.riemann_host, config.riemann_port)
    RiemannClient.tcp(remote)
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    log.info("riemannClient restarting")
    Try(riemannClient.disconnect())
  }


  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    riemannReceiver ! Restarted
  }

  def ready(client: RiemannClient): Receive = {
    system.scheduler.schedule(5 seconds, 5 seconds, self, HeartBeatAlarm)

    riemannReceiver ! Connected(self)

    {
      case Reconnect =>
        riemannClient.reconnect()

      case MetricSeq(metrics) =>
        sendToRiemann(client, metrics)

      case HeartBeatAlarm =>
        sendPeriodicHeartBeat(client)
    }
  }

  def receive: Receive = {
    case Connect =>
      tryConnect(sender())

    case MetricSeq(m) =>
      log.warning("riemann sender is not yet ready")
      riemannReceiver ! Dropped(m.size)

    case _ =>
      log.warning("riemann sender is not yet ready")

  }

  def tryConnect(upstream: ActorRef) = {
    val client = riemannClient
    client.connect()

    sendNewHeartBeat(client) map {
      case Some(reply) if reply.getOk =>
        log.info("Connected to riemann on {}:{}", config.riemann_host, config.riemann_port)
        context become ready(client)
      case Some(reply) =>
        log.error("Could not connect to riemann: {}", reply.getError)
        context stop self
      case None =>
        log.error("Invalid response received from riemann")
        context stop self
    } recover {
      case t =>
        log.error(t, "Could not connect to riemann")
        context stop self
    }
  }

  def sendPeriodicHeartBeat(client: RiemannClient) = {
    sendNewHeartBeat(client) map {
      case Some(m) if m.getOk =>
        log.debug("heartbeat ACKed")
      case Some(m) =>
        log.warning("heartbeat not ACKed by riemann: {}", m.getError)
      case _ =>
        log.warning("heartbeat not ACKed by riemann")
    } recover handleRiemannFailure(List())
  }

  def sendNewHeartBeat(client: RiemannClient) = {
    val sendPromise = client.aSendRecvMessage(heartBeat)
    Future {
      blocking {
        Option (sendPromise.deref(30, TimeUnit.SECONDS))
      }
    }
  }

  def handleRiemannResponse(metrics: Seq[Metric])(msg: Option[Msg]) =
    msg match {
      case Some(m) if m.getOk =>
        riemannReceiver ! Acked(metrics.size)
        log.debug("riemann ack received")
      case Some(m) =>
        riemannReceiver ! Failed(metrics, new AckNotReceivedException)
        log.warning("riemann did not ack msg: {}", m.getError)
      case None =>
        riemannReceiver ! Failed(metrics, new PromiseTimedOutException)
        log.error("Promise timed out")
    }

  def handleRiemannFailure(metrics: Seq[Metric]): PartialFunction[Throwable, Unit] = {
      case t => riemannReceiver ! Failed(metrics, t)
  }

  private def sendToRiemann(client: RiemannClient, metrics: Seq[Metric]) = {
    if (metrics.length > 0) {
      log.debug(s"Sending ${metrics.length} events to riemann")

      val sendPromise = client.aSendRecvMessage(encodedMetrics(metrics))

      riemannReceiver ! Sent(metrics.size)

      Future {
        blocking {
          val startAt = System.currentTimeMillis()
          val result = Option(sendPromise.deref())
          riemannReceiver ! SentFinished(System.currentTimeMillis() - startAt)
          result
        }
      } map handleRiemannResponse(metrics) recover handleRiemannFailure(metrics)
    }
  }

  private def heartBeat = {
    val event =
      Event.newBuilder
        .setHost(InetAddress.getLocalHost.getHostName)
        .setState("ok")
        .setDescription("riepete heartbeat")
        .setService("riepete.heartbeat")
        .addTags("riepete")
        .build

    Msg.newBuilder
      .setOk(true)
      .addEvents(event)
      .build
  }


  private def encodedMetrics(metrics: Seq[Metric]) = {
    val events = metrics.map(x => x.statsdMetric).map(m => {
      Event.newBuilder
        .setHost(InetAddress.getLocalHost.getHostName)
        .setMetricD(m.value)
        .setState("ok")
        .setDescription(m.name)
        .setService(m.name)
        .addAllTags(List("statsd", "riepete", m.metricType))
        .build
    })

    Msg.newBuilder
      .setOk(true)
      .addAllEvents(events)
      .build
  }
}
