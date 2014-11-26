package io.simao.riepete.parser

import scala.util.Try
import scala.util.parsing.combinator.JavaTokenParsers

sealed abstract class ParsedMetric {
  def name: String
  def value: Double
  def metricType = this.getClass.getSimpleName.toLowerCase
}

case class Counter(name: String, value: Double, sampleRate: Option[Double]) extends ParsedMetric
case class Gauge(name: String, value: Double) extends ParsedMetric
case class Timer(name: String, value: Double) extends ParsedMetric
case class Meter(name: String, value: Double) extends ParsedMetric


class StatsParser extends JavaTokenParsers {

  def counter = metric_name ~ metric_value ~ "|c" ~ opt(sample_rate) ^^ {
    case name ~ v ~ _ ~ Some(sample_rate) =>
      Counter(name, v, Some(sample_rate))
    case name ~ v ~ _ ~ None =>
      Counter(name, v, None)
  }

  def gauge = metric_name ~ metric_value <~ "|g" ^^ {
    case name ~ v =>
      Gauge(name, v)
  }

  def timer = metric_name ~ metric_value <~ ("|ms" | "|h") ^^ {
    case name ~ v =>
      Timer(name, v)
  }

  def meter = metric_name ~ metric_value <~ "|m" ^^ {
    case name ~ v =>
      Meter(name, v)
  }

  def metric_name = """[^:|]*""".r <~ ":"

  def metric_value = value ^^ { v => v.toDouble }

  def sample_rate = literal("|@") ~> value ^^ { v => v.toDouble }

  def value = floatingPointNumber | decimalNumber | wholeNumber

  def singleMetric = counter | gauge | timer | meter

  def multipleMetrics = rep1(singleMetric <~ ("""\z""".r | "\n")) <~ rep("\n")

  override val skipWhitespace = false

  def parse[T <: ParsedMetric](metric_str_repr: String): Try[List[T]] = {
    parseAll(multipleMetrics, metric_str_repr) match {
      case Success(result, _) => scala.util.Success(result.asInstanceOf[List[T]])
      case NoSuccess(msg, _) => scala.util.Failure(new Exception(msg))
    }
  }
}

object StatsParser {
  def apply[T <: ParsedMetric](metric_str_repr: String): Try[List[T]] = {
    (new StatsParser).parse(metric_str_repr)
  }
}
