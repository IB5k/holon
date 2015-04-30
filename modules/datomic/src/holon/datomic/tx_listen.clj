(ns holon.datomic.tx-listen
  (:require [holon.datomic.protocols :as p]
            [holon.datomic.utils :refer :all]
            [datomic.api :as d]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component :refer (Lifecycle)]
            [juxt.datomic.extras :refer (DatabaseReference DatomicConnection as-conn as-db to-ref-id to-entity-map EntityReference)]
            [plumbing.core :refer :all]
            [schema.core :as s]))

(s/defrecord DatomicReportQueue
    [connection :- (s/protocol DatomicConnection)]
  Lifecycle
  (start [this]
    (let [tx-reports-ch (async/chan)
          tx-listen-mult (async/mult tx-reports-ch)
          queue (d/tx-report-queue (as-conn connection))]
      (async/thread
        (try (while true
               (let [report (.take queue)]
                 (async/>!! tx-reports-ch report)))
             (catch Exception e
               (log/error "TX-REPORT-TAKE exception: " e)
               (throw e))))
      (assoc this
             :queue queue
             :tx-reports-ch tx-reports-ch
             :tx-listen-mult tx-listen-mult)))
  (stop [this]
    (some-> (as-conn connection) d/remove-tx-report-queue)
    (async/close! (:tx-reports-ch this))
    this)
  p/ListenDatomicReportQueue
  (tap-tx-queue! [this]
    (async/tap (:tx-listen-mult this) (async/chan))))

(s/defrecord DatomicTXListenerAggregator
    [tx-report-queue :- (s/protocol p/ListenDatomicReportQueue)]
  Lifecycle
  (start [this]
    (let [handler (p/tx-handler this)
          ;; will be shutdown on system stop since tx-report-queue closes the queue channel
          reports (p/tap-tx-queue! tx-report-queue)]
      (async/thread
        (loop []
          (when-let [report (async/<!! reports)]
            (handler report))
          (recur))))
    this)
  (stop [this]
    this)
  p/DatomicTXListener
  (tx-handler [this]
    (let [handlers (->> (vals this)
                        (filter #(satisfies? p/DatomicTXListener %))
                        (map p/tx-handler))]
      (fn [tx]
        (doseq [handler handlers]
          (handler tx))))))
