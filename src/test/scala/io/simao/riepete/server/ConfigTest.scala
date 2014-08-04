package io.simao.riepete.server

import org.scalatest.FunSuite

class ConfigTest extends FunSuite {

  test("Create from explicit content") {
    val json = """
{ "riemann_port" : 5555, "riemann_host" : "simao.io", "bind_ip": "0.0.0.1", "bind_port": 8787 }
               """

    val config = Config(json)

    assert(config.riemann_port === 5555)
    assert(config.riemann_host === "simao.io")
    assert(config.bind_ip === "0.0.0.1")
    assert(config.bind_port === 8787)
  }

  test("raises an error when parsing bad json") {
    val json = """lol"""
    intercept[DeserializeConfigException] {
      Config(json)
    }
  }

  test("uses default values if json is empty") {
    val config = Config("{}")

    assert(config.riemann_port === 5555)
    assert(config.riemann_host === "127.0.0.1")
    assert(config.bind_ip === "0.0.0.0")
    assert(config.bind_port === 8787)
  }
}
