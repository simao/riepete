package io.simao.riepete.metric_receivers

import akka.actor.{ActorSystem, Props}
import akka.testkit.{EventFilter, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import io.simao.riepete.messages.{MultiRiepeteMetric, RiepeteMetric, StatsdMetric}
import io.simao.riepete.parser.Counter
import io.simao.riepete.server.Config
import org.scalatest.FunSuiteLike

class StatsReceiverTest extends TestKit(ActorSystem("testSystem")) with FunSuiteLike {
  implicit val riepete_config = Config.default

  test("logs error when metric is invalid") {
    implicit val system = ActorSystem("testLogSystem", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
                                                                              """))
    val receiver = system.actorOf(Props(new StatsReceiver {
      override lazy val receivers = List()
    }))

    EventFilter.warning(start = "Could not parse metric", occurrences = 1).intercept {
      receiver ! StatsdMetric("invalid")
    }
  }

  test("sends metric to it's receivers") {
    val aggregator_probe = TestProbe()

    val receiver = system.actorOf(Props(new StatsReceiver {
      override lazy val receivers = List(aggregator_probe.ref)
    }))

    receiver ! StatsdMetric("hi:99|c")

    val metric = Counter("hi", 99.00, None)

    aggregator_probe.expectMsg(MultiRiepeteMetric(List(RiepeteMetric(metric))))
  }
}
