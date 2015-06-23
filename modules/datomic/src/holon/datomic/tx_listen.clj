(ns holon.datomic.tx-listen
  (:require [holon.datomic.protocols :as p]
            [holon.datomic.utils :refer :all]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component :refer (Lifecycle)]
            [datomic.api :as d]
            [ib5k.component.ctr :as ctr]
            [juxt.datomic.extras :refer (DatabaseReference DatomicConnection as-conn as-db to-ref-id to-entity-map EntityReference)]
            [manifold.bus :as bus]
            [manifold.stream :as stream]
            [plumbing.core :refer :all]
            [schema.core :as s]
            [taoensso.timbre :as log]))

(s/defrecord DatomicReportQueue
    [connection :- (s/protocol DatomicConnection)]
  Lifecycle
  (start [this]
    (let [tx-reports (bus/event-bus)
          queue (d/tx-report-queue (as-conn connection))
          stopped? (atom false)]
      (async/thread
        (while (not @stopped?)
          (try (let [report (.take queue)]
                 (bus/publish! tx-reports :tx report))
               (catch Exception e
                 (log/error "TX-REPORT-TAKE exception: " e)
                 e))))
      (assoc this
             :stop! (fn []
                      (d/remove-tx-report-queue (as-conn connection))
                      (reset! stopped? true))
             :tx-reports tx-reports)))
  (stop [this]
    (some-> this :stop! (apply []))
    this)
  p/DatomicReportStream
  (tx-stream [this]
    (bus/subscribe (:tx-reports this) :tx)))

(def new-datomic-report-queue
  (-> map->DatomicReportQueue
      (ctr/wrap-class-validation DatomicReportQueue)
      (ctr/wrap-using [:connection])
      (ctr/wrap-kargs)))

(s/defrecord DatomicTXListenerAggregator
    [tx-report-queue :- (s/protocol p/DatomicReportStream)]
  Lifecycle
  (start [this]
    (let [handler (p/tx-handler this)
          ;; will be shutdown on system stop since tx-report-queue closes the queue channel
          reports (p/tx-stream tx-report-queue)]
      (async/thread
        (stream/consume handler reports))
      (assoc this
             :stop! (fn []
                      (stream/close! reports)))))
  (stop [this]
    (some-> this :stop! (apply []))
    this)
  p/DatomicTXListener
  (tx-handler [this]
    (let [handlers (->> (vals this)
                        (filter #(satisfies? p/DatomicTXListener %))
                        (map p/tx-handler))]
      (fn [tx]
        (doseq [handler handlers]
          (handler tx))))))

(def new-datomic-tx-listener-aggregator
  (-> map->DatomicTXListenerAggregator
      (ctr/wrap-class-validation DatomicTXListenerAggregator)
      (ctr/wrap-using [:tx-report-queue])
      (ctr/wrap-kargs)))
