package io.simao.riepete.server

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.io.Udp
import akka.testkit.{TestKit, TestProbe}
import akka.util.ByteString
import io.simao.riepete.messages.StatsdMetric
import org.scalatest.FunSuiteLike

import scala.language.postfixOps

class RiepeteServerTest extends TestKit(ActorSystem("riemannServerTestSystem")) with FunSuiteLike {
  implicit val riepete_config = Config.default

  val testStatsdHandler = TestProbe()

  val testServer = system.actorOf(Props(new RiepeteServer {
    override def preStart(): Unit = { }

    override lazy val statsdHandler = testStatsdHandler.ref
  }))

  test("forwards metrics after Udp.Bound") {
    val remote = new InetSocketAddress(2323)
    val payload = ByteString("hi:99|c")

    testServer ! Udp.Bound(remote)

    testServer ! Udp.Received(payload, remote)

    testStatsdHandler.expectMsg(StatsdMetric("hi:99|c", "0.0.0.0"))
  }
}
