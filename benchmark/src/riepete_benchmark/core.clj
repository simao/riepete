(ns riepete-benchmark.core
  (:import (org.apache.commons.math3.distribution PoissonDistribution))
  (:require [clojure.tools.logging :as log])
  (:gen-class))

(defn next-val [dist]
  (.sample dist))

(defn -run-limited [fn max]
  "Returns true if `fn' took more than `max' milliseconds to execute"
  (let [start-at (System/currentTimeMillis)]
    (fn)
    (> (- (System/currentTimeMillis) start-at) max)))

(defn -send-packets-async [message-fn count-msg max-time]
  "Async send of `count-msg' udp metrics, checks if sending took more
  than `max-time' and logs a message if it did "
  (future (when (-run-limited #(message-fn count-msg) max-time)
            (log/error "Sending UDP packet took too long, results are probably wrong!"))))

(defn send-messages-with [dist sleep-time message-fn max-sent]
  "Use `message-fn' to send x number of messages every `sleep-time'
  milliseconds, where x is a random bumber distributed according to
  `dist' "
  (let [start-at (System/currentTimeMillis)
        elapsed-ms #(- (System/currentTimeMillis) start-at)]
    (loop [sent 0
           last-sleep 0
           current-interval-sent-count 0
           send-count (next-val dist)]
      (-send-packets-async message-fn send-count sleep-time)
      (Thread/sleep sleep-time)
      (let [elapsed-time (elapsed-ms)
            output-stats? (> (- elapsed-time last-sleep) 5000)
            last-stats-output (if output-stats? elapsed-time last-sleep)
            last-interval-sent-count (+ current-interval-sent-count send-count)
            next-interval-sent-count (if output-stats? 0 last-interval-sent-count)
            ]
        (if output-stats?
          (do
            (log/info "Sent" sent "messages, msg/sec" (float (/ sent (/ elapsed-time 1000))))
            (log/info "Sent" last-interval-sent-count "in last 5 seconds")))

        (if (< sent max-sent)
          (recur (+ sent send-count) last-stats-output next-interval-sent-count (next-val dist))
          (log/info "Finished sending" (+ sent send-count) "messages"))))))


(defn send-msg-fn [address port]
  "Returns a function that can send `count' messages to the specified
  address:port keeping a connected datagram socket"
  (let [socket-address (java.net.InetSocketAddress. address port)
        socket (java.net.DatagramSocket.)]
    (.connect socket socket-address)
    (fn send-msg-connected-socket-fn [count]
      (let [msg (.getBytes "benchmark:1|c")
            packet (java.net.DatagramPacket. msg (alength msg) socket-address)]
        (when (> count 0)
          (do (.send socket packet)
              (recur (- count 1))))))))

(defn -main
  ""
  [& args]
  (let [lambda-sec (read-string (first args))
        max-sent (read-string (second args))
        riepete-address (nth args 2 "0.0.0.0")
        sleep-time 10
        lambda (/ (* sleep-time lambda-sec) 1000)
        message-fn (send-msg-fn riepete-address 8787)
        dist (PoissonDistribution. lambda)
        ]
    (log/info "Using λ=" lambda-sec "msg/s")
    (send-messages-with dist sleep-time message-fn max-sent)))
