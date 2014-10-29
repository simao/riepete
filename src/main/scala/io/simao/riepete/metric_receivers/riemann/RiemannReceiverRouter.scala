package io.simao.riepete.metric_receivers.riemann

import akka.actor.{Actor, ActorLogging, Props, SupervisorStrategy}
import akka.pattern.ask
import akka.routing.{Routees, GetRoutees, DefaultResizer, RoundRobinPool}
import io.simao.riepete.server.Config

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

case object StatsOutputAlarm

object RiemannReceiverRouter {
  def props()(implicit config: Config) = {
    Props(new RiemannReceiverRouter)
  }
}

// TODO: When routees are backing, we still send them metrics that will be dropped
// Should route only to routees that are known to be `Sending`
// This backoff shit is giving a lot of problems

class RiemannReceiverRouter(implicit config: Config) extends Actor with ActorLogging {
  lazy val statsKeeper = context.actorOf(Props[RiemannConnectionStatsKeeper], "riemannStatsKeeper")

  implicit val ec = ExecutionContext.global

  context.system.scheduler.schedule(5 seconds, 5 seconds)(outputStats())

  val resizer = DefaultResizer(lowerBound = 3, upperBound = 20,
    messagesPerResize = 100, pressureThreshold = 50, backoffThreshold = 0)

  var lastStats = Map[String, Int]()

  lazy val router = context.actorOf(
  RiemannReceiver.props(statsKeeper)
    .withMailbox("riemann-receivers-mailbox")
    .withRouter(RoundRobinPool(3)
      .withResizer(resizer)
      .withSupervisorStrategy(SupervisorStrategy.defaultStrategy)),
    "riemannReceiverRouterImpl")

  def outputStats() = {
    router.ask(GetRoutees)(1 second)
      .mapTo[Routees]
      .map { case Routees(r) => log.info(s"Receivers: ${r.size}")}

    statsKeeper
      .ask(GetResetIntervalStats)(1 second)
      .mapTo[Map[String, AnyRef]]
      .onComplete {
      case Success(stats) => log.info(stats.mkString(", "))
      case Failure(error) => log.error(error, "Could not get stats")
    }
  }

  def receive: Receive = {
    case m => router ! m
  }
}
