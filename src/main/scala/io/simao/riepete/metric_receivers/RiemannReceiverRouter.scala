package io.simao.riepete.metric_receivers

import akka.actor.{Props, Actor, ActorLogging, SupervisorStrategy}
import akka.routing.RoundRobinPool
import io.simao.riepete.server.Config
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.pattern.ask

import scala.util.{Failure, Success}

case object StatsOutputAlarm

object RiemannReceiverRouter {
  def props()(implicit config: Config) = {
    Props(new RiemannReceiverRouter)
  }
}

// TODO: When routees are backing, we still send them metrics that will be dropped
// Should route only to routees that are known to be `Sending`
class RiemannReceiverRouter(implicit config: Config) extends Actor with ActorLogging {
  lazy val statsKeeper = context.actorOf(Props[ConnectionStatsKeeper], "riemannStatsKeeper")

  implicit val ec = ExecutionContext.global

  context.system.scheduler.schedule(5 seconds, 5 seconds)(outputStats())

  // TODO: Maybe use dynamic resizer?
  var router = {
    context.actorOf(
      RoundRobinPool(8)
        .withSupervisorStrategy(SupervisorStrategy.defaultStrategy)
        .props(RiemannReceiver.props(statsKeeper)),
      "riemannReceiverRouterImpl")
  }

  def outputStats() = {
    statsKeeper
      .ask(StatsRequest)(1 second)
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
