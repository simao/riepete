package io.simao.riepete.server

import akka.actor.{ActorSystem, Props}
import akka.testkit.{EventFilter, TestKit, TestProbe}
import io.simao.riepete.messages.{Metric, MetricSeq, StatsdMetric}
import io.simao.riepete.parser.Counter
import org.scalatest.FunSuiteLike

class StatsdMetricHandlerTest extends TestKit(ActorSystem("testSystem")) with FunSuiteLike {
  implicit val riepete_config = Config.default

  val aggregator_probe = TestProbe()

  def testReceiver = system.actorOf(Props(new StatsdMetricHandler {
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

    aggregator_probe.expectMsg(MetricSeq(List(Metric(metric, "simao.io"))))
  }
}
