package io.simao.riepete.metric_receivers.riemann

import akka.actor._
import io.simao.riepete.messages.{Metric, MetricSeq}
import io.simao.riepete.server.Config

import scala.concurrent.duration._
import scala.language.postfixOps


sealed trait ControlMsg
case class Connected(riemann: ActorRef) extends ControlMsg
case object Connect extends ControlMsg
case object Flush extends ControlMsg
case object BackOffTimeout extends ControlMsg
case object ConnectTimeout extends ControlMsg

sealed trait State
case object Idle extends State
case object Connecting extends State
case object Sending extends State

sealed trait Data
final case class CurrentData(riemann: Option[ActorRef], buffer: Seq[Metric]) extends Data

object RiemannReceiver {
  def props(statsKeeper: ActorRef)(implicit config: Config) = {
    Props(new RiemannReceiver(statsKeeper))
  }
}

class RiemannReceiver(statsKeeper: ActorRef)(implicit config: Config) extends Actor with ActorLogging with FSM[State, Data] {
  override def postStop(): Unit = {
    super.postStop()
    log.info("Terminating")
  }

  lazy val riemannSender = {
    context.actorOf(RiemannClientActor.props(),
      s"riemannClient-${self.path.name}")
  }

  startWith(Idle, CurrentData(None, Vector.empty), Some(100 millis))

  onTransition {
    case _ -> Sending =>
      setTimer("flushTimer", Flush, 1 second, repeat = true)

    case Sending -> _ =>
      cancelTimer("flushTimer")
  }

  onTransition {
    case m -> Connecting =>
      tryConnect()
      setTimer("connectTimeout", ConnectTimeout, 5 seconds)

    case Connecting -> _ =>
      cancelTimer("connectTimeout")
  }

  when(Idle) {
    case Event(StateTimeout, _) =>
      goto(Connecting)

    case Event(MetricSeq(metrics), cd: CurrentData) =>
      goto(Connecting) using cd.copy(buffer = cd.buffer ++ metrics)
  }

  when(Connecting) {
    case Event(MetricSeq(metrics), cd @ CurrentData(_, b)) =>
      stay() using cd.copy(buffer = b ++ metrics)

    case Event(Connected(riemann), cd @ CurrentData(_, b)) =>
      goto(Sending) using CurrentData(Some(riemann), b)

    case Event(ConnectTimeout, CurrentData(_, b)) =>
      log.warning("Connect timeout")
      goto(Idle) forMax(1 second)
  }

  when(Sending) {
    case Event(MetricSeq(metrics), cd @ CurrentData(Some(riemann), b)) =>
      stay() using cd.copy(buffer = b ++ metrics)

    case Event(Flush, cd @ CurrentData(Some(riemann), b)) =>
      val newBuffer = flush(riemann, b)
      stay() using cd.copy(buffer = newBuffer)

    case Event(f @ Failed(metrics, cause), cd @ CurrentData(Some(riemann), b)) =>
      statsKeeper ! f
      reconnect(riemann, cause)
      stay()
  }

  def tryConnect() {
    riemannSender ! Connect
  }

  def reconnect(riemann: ActorRef, cause: Throwable) = {
    log.error(cause, "reconnecting to riemann")
    riemann ! Reconnect
  }

  def flush(riemann: ActorRef, buffer: Seq[Metric]): Seq[Metric] = {
    if (buffer.size > 0) {
      riemann ! MetricSeq(buffer)
    } else
      log.debug("Buffer is empty, nothing flushed")

    Vector.empty
  }

  whenUnhandled {
    case Event(c: ConnectionStat, _) =>
      statsKeeper ! c
      stay()

    case Event(Restarted, _) =>
      goto(Idle) forMax(1 second)
  }

  onTransition {
    case a -> b =>
      log.debug("{} => {}", a, b)
  }

  initialize()
}
