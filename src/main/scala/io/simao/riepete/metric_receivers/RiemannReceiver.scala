package io.simao.riepete.metric_receivers

import akka.actor._
import io.simao.riepete.messages.{MultiRiepeteMetric, RiepeteMetric}
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
case object BackingOff extends State
case object Sending extends State

sealed trait Data
final case class CurrentData(riemann: Option[ActorRef], buffer: Seq[RiepeteMetric], currentBackOff: Option[FiniteDuration] = None) extends Data

object RiemannReceiver {
  def props(statsKeeper: ActorRef)(implicit config: Config) = {
    Props(new RiemannReceiver(statsKeeper))
  }
}

class RiemannReceiver(statsKeeper: ActorRef)(implicit config: Config) extends Actor with ActorLogging with FSM[State, Data] {
  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  def createRiemannSender() = {
    context.actorOf(RiemannSender.props(), "riemannSender")
  }

  startWith(Idle, CurrentData(None, Vector.empty), Some(100 millis))

  onTransition {
    case _ -> Sending =>
      setTimer("flushTimer", Flush, 1 second, repeat = true)

    case Sending -> _ =>
      cancelTimer("flushTimer")
  }

  onTransition {
    case _ -> Connecting =>
      tryConnect()
      setTimer("connectTimeout", ConnectTimeout, 5 seconds)

    case Connecting -> _ =>
      cancelTimer("connectTimeout")
  }

  onTransition {
    case _ -> BackingOff =>
      nextStateData match {
        case CurrentData(_, _, t) =>
          val nextTimeout = t.getOrElse(1 second)
          setTimer("backoffTimeout", BackOffTimeout, nextTimeout)
          log.warning("Backing off for {} seconds", nextTimeout.toSeconds)
      }
  }

  when(Idle) {
    case Event(StateTimeout, _) =>
      goto(Connecting)

    case Event(MultiRiepeteMetric(metrics), cd: CurrentData) =>
      goto(Connecting) using cd.copy(buffer = cd.buffer ++ metrics)
  }

  when(Connecting) {
    case Event(MultiRiepeteMetric(metrics), cd @ CurrentData(_, b, _)) =>
      stay() using cd.copy(buffer = b ++ metrics)

    case Event(Connected(riemann), cd @ CurrentData(_, b, _)) =>
      goto(Sending) using CurrentData(Some(riemann), b, None)

    case Event(ConnectTimeout | Terminated(_), CurrentData(_, b, t)) =>
      val nextTimeout = nextBackOffTimeout(t)
      goto(BackingOff) using CurrentData(None, b, Some(nextTimeout))
  }

  when(BackingOff) {
    case Event(MultiRiepeteMetric(metrics), _) =>
      statsKeeper ! Dropped(metrics.size)
      log.debug("Backing off, dropping {} metrics", metrics)
      stay()

    case Event(BackOffTimeout, cd) =>
      goto(Connecting) using cd

    case Event(Failed(metrics, cause), cd: CurrentData) =>
      stay() using cd.copy(buffer = cd.buffer ++ metrics)
  }

  when(Sending) {
    case Event(MultiRiepeteMetric(metrics), cd @ CurrentData(Some(riemann), b, _)) =>
      stay() using cd.copy(buffer = b ++ metrics)

    case Event(Flush, cd @ CurrentData(Some(riemann), b, _)) =>
      val newBuffer = flush(riemann, b)
      stay() using cd.copy(buffer = newBuffer)

    case Event(Terminated(riemannSender), CurrentData(_, b, _)) =>
      goto(BackingOff) using CurrentData(None, b, Some(1 second))

    case Event(Failed(metrics, cause), cd @ CurrentData(Some(riemann), b, _)) =>
      disconnect(riemann, cause)
      goto(BackingOff) using CurrentData(None, metrics ++ b)
  }

  def tryConnect() {
    val riemannSender = createRiemannSender()
    context watch riemannSender
    riemannSender ! Connect
  }

  def disconnect(riemann: ActorRef, cause: Throwable) = {
    log.error(cause, "Disconnecting from riemann")
    context unwatch riemann
    context stop riemann
  }

  def flush(riemann: ActorRef, buffer: Seq[RiepeteMetric]): Seq[RiepeteMetric] = {
    if (buffer.size > 0) {
      riemann ! MultiRiepeteMetric(buffer)
    } else
      log.debug("Buffer is empty, nothing flushed")

    Vector.empty
  }

  def nextBackOffTimeout(currentTimeout: Option[FiniteDuration]) = {
    (currentTimeout.getOrElse(1 second) * 2).min(30 seconds)
  }

  whenUnhandled {
    case Event(c: ConnectionStat, _) =>
      statsKeeper ! c
      stay()
  }

  onTransition {
    case a -> b =>
      log.info("{} => {}", a, b)
  }

  initialize()
}
