package io.simao.riepete.metric_receivers

import akka.actor.{ActorSystem, Props}
import akka.testkit.{EventFilter, TestKit, TestProbe}
import io.simao.riepete.messages.{MultiRiepeteMetric, RiepeteMetric, StatsdMetric}
import io.simao.riepete.parser.Counter
import io.simao.riepete.server.Config
import org.scalatest.FunSuiteLike

class StatsReceiverTest extends TestKit(ActorSystem("testSystem")) with FunSuiteLike {
  implicit val riepete_config = Config.default

  val aggregator_probe = TestProbe()

  def testReceiver = system.actorOf(Props(new StatsReceiver {
    override lazy val receivers = List(aggregator_probe.ref)
  }))

  test("logs error when metric is invalid") {
    EventFilter.warning(start = "Could not parse metric", occurrences = 1).intercept {
      testReceiver ! StatsdMetric("invalid", "localhost")
    }
  }

  test("sends metric to it's receivers") {
    testReceiver ! StatsdMetric("hi:99|c", "simao.io")

    val metric = Counter("hi", 99.00, None)

    aggregator_probe.expectMsg(MultiRiepeteMetric(List(RiepeteMetric(metric, "simao.io"))))
  }
}
