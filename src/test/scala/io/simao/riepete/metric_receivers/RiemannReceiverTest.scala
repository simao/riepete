package io.simao.riepete.metric_receivers

import akka.actor._
import akka.testkit._
import io.simao.riepete.messages.{MultiRiepeteMetric, RiepeteMetric}
import io.simao.riepete.parser.Counter
import io.simao.riepete.server.Config
import org.scalatest.{FunSuiteLike, OneInstancePerTest}

class RiemannReceiverTest extends TestKit(ActorSystem("testSystem")) with FunSuiteLike with ImplicitSender with OneInstancePerTest {
  implicit val riepete_config = Config.default

  lazy val testRiemannSender = TestProbe()

  val statsKeeper = TestProbe()

  val receiver = TestFSMRef(new RiemannReceiver(statsKeeper.ref) {
    override def createRiemannSender() = testRiemannSender.ref
  })

  val singleMetric = RiepeteMetric(Counter("hi", 1, None))

  val testMetric = MultiRiepeteMetric(List(singleMetric))

  test("is connected after receiving Connected confirmation") {
    testRiemannSender.expectMsg(Connect)

    receiver ! Connected(testRiemannSender.ref)

    assert(receiver.stateName == Sending)
  }

  test("backs of if connection dies") {
    testRiemannSender.expectMsg(Connect)

    receiver ! Connected(testRiemannSender.ref)

    assert(receiver.stateName == Sending)

    testRiemannSender.ref ! PoisonPill

    assert(receiver.stateName == BackingOff)
  }

  test("backs off if connection times out") {
    receiver.setState(Connecting, CurrentData(None, Vector(), None))

    assert(receiver.isTimerActive("connectTimeout") === true)
    receiver ! ConnectTimeout

    assert(receiver.stateName === BackingOff)
  }

  test("buffers metrics while connecting") {
    testRiemannSender.expectMsg(Connect)

    receiver ! testMetric
    receiver ! testMetric

    assert(receiver.stateName === Connecting)

    receiver ! Connected(testRiemannSender.ref)

    assert(receiver.stateName === Sending)
    testRiemannSender.expectMsg(MultiRiepeteMetric(List(singleMetric, singleMetric)))
  }

  test("metrics are buffering in the correct order, arrival order") {
    testRiemannSender.expectMsg(Connect)
    receiver ! Connected(testRiemannSender.ref)

    val m1 = RiepeteMetric(Counter("hi", 1, None))
    val m2 = RiepeteMetric(Counter("hi", 2, None))
    val m3 = RiepeteMetric(Counter("hi", 3, None))

    receiver ! MultiRiepeteMetric(List(m3))
    receiver ! MultiRiepeteMetric(List(m2))
    receiver ! MultiRiepeteMetric(List(m1))

    testRiemannSender.expectMsg(MultiRiepeteMetric(List(m3, m2, m1)))
  }

  test("drops metrics when backing off") {
    receiver.setState(BackingOff, CurrentData(None, Vector(), None))

    receiver ! testMetric

    statsKeeper.expectMsg(Dropped(1))
  }

  test("forwards multi riepete metrics") {
    testRiemannSender.expectMsg(Connect)

    receiver ! Connected(testRiemannSender.ref)

    val metrics = MultiRiepeteMetric(List(singleMetric, singleMetric))

    receiver ! metrics

    testRiemannSender.expectMsg(metrics)
  }

  test("backs off if sending fails (receives a Failed msg)") {
    testRiemannSender.expectMsg(Connect)

    receiver ! Connected(testRiemannSender.ref)

    receiver ! Failed(List(), new Exception("error occured"))

    assert(receiver.stateName == BackingOff)
  }

  test("forward stats to statsKeeper") {
    receiver ! Sent(100)
    statsKeeper.expectMsg(Sent(100))
  }

  test("retries after when backing off and riemannSender dies") {
    receiver.setState(BackingOff, CurrentData(None, Vector(), None))

    assert(receiver.isTimerActive("backoffTimeout") === true)

    testRiemannSender.expectMsg(Connect)
  }
}
