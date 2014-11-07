package io.simao.riepete.server

import argonaut._, Argonaut._

import scala.io.Source
import scalaz.{\/-, -\/}


class Config(serialized_config: JsonConfig) {
  def riemann_host = serialized_config.riemann_host.getOrElse("127.0.0.1")
  def riemann_port = serialized_config.riemann_port.getOrElse(5555)

  def bind_ip = serialized_config.bind_ip.getOrElse("0.0.0.0")
  def bind_port = serialized_config.bind_port.getOrElse(8787)
}

class DeserializeConfigException(msg: String) extends RuntimeException

case class JsonConfig(riemann_host: Option[String], riemann_port: Option[Int], bind_ip: Option[String], bind_port: Option[Int])

object JsonConfig {
  implicit def jsonConfigCodecJson: CodecJson[JsonConfig] =
    casecodec4(JsonConfig.apply, JsonConfig.unapply)("riemann_host", "riemann_port", "bind_ip", "bind_port")
}

object Config {
  def default = Config("{}")

  def apply(s: Source): Config = {
    val content = s.mkString
    apply(content)
  }

  def apply(content: String): Config = {
    Parse.decodeEither[JsonConfig](content) match {
      case \/-(config) => new Config(config)
      case -\/(msg) => throw new DeserializeConfigException("Error parsing config file: " + msg)
    }
  }
}
