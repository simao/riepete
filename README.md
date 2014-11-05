# Riepete - Riemann Repeater

Receives [statsd](https://github.com/etsy/statsd/) packets and repeats
them to [riemann](http://riemann.io/).

[![Build Status](https://travis-ci.org/simao/riepete.png?branch=master)](https://travis-ci.org/simao/riepete)
 
## Supported metrics

Currently riepete supports as subset of statsd metrics as defined in
https://github.com/etsy/statsd/blob/master/docs/metric_types.md.

In particular, riepete does not support:

- [sets](https://github.com/etsy/statsd/blob/master/docs/metric_types.md#sets)

- gauges with `-`/`+` modifiers.

  Gauges will still be parsed and accepted by riepete, but they will
  be mapped into metrics with the received positive/negative value and
  sent to riemann.

## Metrics send to riemann

For each metric received by riepete a new event is sent to riemann.

The event sent to riemann has:

- Its `status` set to `ok`
- Its `hostname` set to the hostname where riepete is running
- Its `description` and `service` set to the key name used in the statsd metric
- Its `tags` set to `statsd, riepete, <metric_type>` where
  `<metric_type>` is one of `Counter`, `Gauge` or `Timer`.

## Caveats

Metrics are repeated to riemann without any type of aggregation. Each
statsd UDP packet will result in a metric being sent to riemann. If
you need some kind of aggregation you will need to do it in
riemann. See the next section for an example on how to do this.

## Installing

The latest version is available in `releases/riepete-latest.tgz`.

You can extract this file into a directory and just run
`bin/start`. This will start riepete in the foregrou with the default
settings. Here is one liner if you just want to try running it:

```
wget https://github.com/simao/riepete/releases/download/v0.0.2/riepete-0.0.2.tgz
tar xvf riepete-0.0.2.tgz
cd riepete-0.0.2
bin/start
```

You can adjust settings editing `riepete/config/riepete.json`.

You can read more about deploying an akka app in
[this post](https://simao.io/blog/2014/10/10/deploying-an-akka-app)

Alternatively, you can clone this repo and built riepete with `sbt
dist`.

## Example riemann.config file

Since riepete does not aggregate any metrics, you will need to do this
yourself if you need to show aggregated results.

This section shows some examples you can you in your `riemann.config`.

### Rate of events received from riepete

This creates an event every five seconds with with `:metric` set to
the number of events received per second in that interval.

```clojure
(tagged "riepete"
  (with {:metric 1 :state "ok" :service "riepete-events/sec"}
        (rate 5 index)))
```
   
### Calculate rate of events for each statsd metric type

This generates a new event every 5 seconds with `:metric` set to the
rate per second of the last 5 seconds. For example,
`riepete-timers/sec` will show you how many timers per second riemann
received from riepete in the last 5 seconds.

```clojure
(defn as-rate-if-tagged [tags new-name & children]
    (tagged tags
            (with {:metric 1 :state "ok" :service new-name}
                  (rate 5
                        (fn stream [event]
                          (call-rescue event children))))))

(as-rate-if-tagged ["riepete" "timer"] "riepete-timers/sec" index)

(as-rate-if-tagged ["riepete" "counter"] "riepete-counters/sec" index)

(as-rate-if-tagged ["riepete" "gauge"] "riepete-gauges/sec" index)
```


### Calculate rate of events for all counters

This generates a new event for each counter received from
riemann. Each generated event will have the rate of the corresponding
event in the last 5 seconds. Since riepete does not use any
aggregation, this must be done by riepete, but it's as easy as using
this simple code:

```clojure
(tagged-all ["riepete" "statsd" "counter"]
            (by :service
                (adjust [:service #(str "stats.counts." % ".rate")]
                        (rate 5 index))))
```

## Benchmarking

In the `benchmark` directory you will find a small clojure app that
can be used to send a large number of requests to riepete using a non
uniform distribution.

This benchmark was executed running on an ec2 c3.large instance
sending images to another c3.large instance where riepete was
deployed.

Riepete was able to handle and forward to riemann about 12000
events/second, before the os started to drop UDP packets.

Benchmarks are just benchmarks and this one certainly has it's
problems and can be improved, increasing UDP buffer sizes for
example. I will explore these possibilities in future releases. For
now 12000 event/s is enough for the purposes `riepete` is being used.

## Contributing

Riepete is written in Scala and uses Akka.

No pull request is too small, documentation improvements also very
welcome!

## Author

- Simão Mata - [simao.io](https://simao.io)

