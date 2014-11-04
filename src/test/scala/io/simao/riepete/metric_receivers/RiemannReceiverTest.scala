package io.simao.riepete.metric_receivers

import akka.actor._
import akka.testkit._
import io.simao.riepete.messages.{MetricSeq, Metric}
import io.simao.riepete.metric_receivers.riemann._
import io.simao.riepete.parser.Counter
import io.simao.riepete.server.Config
import org.scalatest.{FunSuiteLike, OneInstancePerTest}

class RiemannReceiverTest extends TestKit(ActorSystem("testSystem")) with FunSuiteLike with ImplicitSender with OneInstancePerTest {
  implicit val riepete_config = Config.default

  lazy val testRiemannSender = TestProbe()

  val statsKeeper = TestProbe()

  val receiver = TestFSMRef(new RiemannReceiver(statsKeeper.ref) {
    override lazy val riemannSender = testRiemannSender.ref
  })

  val singleMetric = Metric(Counter("hi", 1, None))

  val testMetric = MetricSeq(List(singleMetric))

  test("is connected after receiving Connected confirmation") {
    testRiemannSender.expectMsg(Connect)

    receiver ! Connected(testRiemannSender.ref)

    assert(receiver.stateName == Sending)
  }

  test("idles if it receives a Started message") {
    testRiemannSender.expectMsg(Connect)

    receiver ! Connected(testRiemannSender.ref)

    assert(receiver.stateName == Sending)

    receiver ! Restarted

    assert(receiver.stateName == Idle)
  }

  test("idles if connection times out") {
    receiver.setState(Connecting, CurrentData(None, Vector()))

    assert(receiver.isTimerActive("connectTimeout") === true)
    receiver ! ConnectTimeout

    assert(receiver.stateName === Idle)
  }

  test("buffers metrics while connecting") {
    testRiemannSender.expectMsg(Connect)

    receiver ! testMetric
    receiver ! testMetric

    assert(receiver.stateName === Connecting)

    receiver ! Connected(testRiemannSender.ref)

    assert(receiver.stateName === Sending)
    testRiemannSender.expectMsg(MetricSeq(List(singleMetric, singleMetric)))
  }

  test("metrics are buffering in the correct order, arrival order") {
    testRiemannSender.expectMsg(Connect)
    receiver ! Connected(testRiemannSender.ref)

    val m1 = Metric(Counter("hi", 1, None))
    val m2 = Metric(Counter("hi", 2, None))
    val m3 = Metric(Counter("hi", 3, None))

    receiver ! MetricSeq(List(m3))
    receiver ! MetricSeq(List(m2))
    receiver ! MetricSeq(List(m1))

    testRiemannSender.expectMsg(MetricSeq(List(m3, m2, m1)))
  }

  test("forwards multi riepete metrics") {
    testRiemannSender.expectMsg(Connect)

    receiver ! Connected(testRiemannSender.ref)

    val metrics = MetricSeq(List(singleMetric, singleMetric))

    receiver ! metrics

    testRiemannSender.expectMsg(metrics)
  }

  test("forces a reconnect when sending fails") {
    testRiemannSender.expectMsg(Connect)

    receiver ! Connected(testRiemannSender.ref)

    receiver ! Failed(List(), new Exception("error occured"))

    testRiemannSender.expectMsg(Reconnect)
  }

  test("forward stats to statsKeeper") {
    receiver ! Sent(100)
    statsKeeper.expectMsg(Sent(100))
  }
}
