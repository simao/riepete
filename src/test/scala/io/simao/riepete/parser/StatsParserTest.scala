package io.simao.riepete.parser

import org.scalatest.FunSuite
import org.scalatest.prop.PropertyChecks

class StatsParserTest extends FunSuite with PropertyChecks {

  def singleMetric(repr: String) = {
    StatsParser(repr).get.apply(0)
  }

  test("fails on unknown metric") {
    val metric = StatsParser("mycounter:|z")
    assert(metric.isFailure)
  }

  test("parses counter name") {
    forAll { (name: String, value: Int) =>
      val repr = s"$name:$value|c"
      val metric = singleMetric(repr).asInstanceOf[Counter]
      assert(metric.name === name)
      assert(metric.value === value)
    }
  }

  test("parses double values") {
    forAll { (value: Double) =>
      val repr = s"mycounter:$value|c"
      val metric = singleMetric(repr).asInstanceOf[Counter]
      assert(metric.name === "mycounter")
      assert(metric.value === value)
    }
  }

  test("parses integer values") {
    forAll { (value: Int) =>
      val repr = s"mycounter:$value|c"
      val metric = singleMetric(repr).asInstanceOf[Counter]
      assert(metric.name === "mycounter")
      assert(metric.value === value)
    }
  }

  test("can parse a simple counter") {
    val metric = singleMetric("mycounter:1|c").asInstanceOf[Counter]

    assert(metric.name === "mycounter")
    assert(metric.value === 1.0)
    assert(metric.sampleRate === None)
    assert(metric.metricType === "counter")
  }

  test("can parse multiple metrics in one package") {
    forAll { (a: Double, b: Int, c: Double) =>
      val repr =
        s"""mycounter:$a|c
          |mycounter2:$b|c
          |mycounter:$c|c
          |""".stripMargin

      assert(StatsParser(repr).isSuccess)
    }
  }

  test("parses the correct number of metrics in one package") {
    forAll { (a: Double, b: Int, c: Double, timerName: String) =>
      whenever(!timerName.contains(":") && !timerName.contains("|")) {
        val repr =
          s"""mycounter:$a|c
          |$timerName:$b|c
          |mycounter:$c|c
          |""".stripMargin

        val metrics = StatsParser(repr).get

        assert(metrics.length == 3)
        assert(metrics(1).name === timerName)
        assert(metrics(2).value === c)
      }
    }
  }

  test("parse a counter with sample rate") {
    forAll { (value: Double, rate: Double) =>
      val repr = s"mycounter:$value|c|@$rate"
      val metric = singleMetric(repr).asInstanceOf[Counter]

      assert(metric.name === "mycounter")
      assert(metric.value === value)
      assert(metric.sampleRate.get === rate)
    }
  }

  test("parse a gauge") {
    forAll { (value: Double, name: String) =>
      whenever(!name.contains(":")) {
        val repr = s"$name:$value|g"
        val metric = singleMetric(repr).asInstanceOf[Gauge]

        assert(metric.name === name)
        assert(metric.value === value)
      }
    }
  }

  test("parse a timer") {
    val metric = singleMetric("my.timer.wat:2122.0|ms").asInstanceOf[Timer]

    assert(metric.name === "my.timer.wat")
    assert(metric.value === 2122.0)
  }

  test("parse a meter") {
    val metric = singleMetric("my.meter.wat:10|m").asInstanceOf[Meter]

    assert(metric.name === "my.meter.wat")
    assert(metric.value === 10.0)
  }
}
