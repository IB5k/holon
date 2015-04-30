(ns holon.datomic.database
  (:require [holon.datomic.protocols :as p]
            [holon.datomic.utils :refer :all]
            [datomic.api :as d]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.tools.reader]
            [clojure.tools.reader.reader-types :refer (indexing-push-back-reader)]
            [com.stuartsierra.component :as component :refer (Lifecycle)]
            [ib5k.component.ctr :as ctr]
            [io.rkn.conformity :as c]
            [juxt.datomic.extras :refer (DatabaseReference DatomicConnection as-conn as-db to-ref-id to-entity-map EntityReference)]
            [plumbing.core :refer :all]
            [schema.core :as s]
            [taoensso.timbre :as log]))

(s/defrecord EphemeralDatabase
    [uri :- s/Str]
  Lifecycle
  (start [this]
    (d/create-database uri)
    this)
  (stop [this]
    (d/delete-database uri)
    (d/shutdown false)
    this)
  p/DatomicDatabase
  (url [this]
    uri))

(s/defrecord DurableDatabase
    [uri :- s/Str]
  Lifecycle
  (start [this]
    (d/create-database uri)
    this)
  (stop [this]
    (d/shutdown false)
    this)
  p/DatomicDatabase
  (url [this]
    uri))

(defnk make-datomic-uri
  [ephemeral? uri db-name {aws-secret-key nil} {aws-access-key-id nil} :as opts]
  (cond-> (str uri db-name)
    ephemeral? (str "-" (uuid))
    (and aws-secret-key
         aws-access-key-id) (str (as-query-string (select-keys opts [:aws-secret-key :aws-access-key-id])))))

(def new-datomic-database
  (-> (fnk [ephemeral? :as opts]
        ((if ephemeral?
           map->EphemeralDatabase
           map->DurableDatabase)
         {:uri (make-datomic-uri opts)}))
      (ctr/wrap-class-validation DurableDatabase)
      (ctr/wrap-validation {:uri s/Str
                              :db-name s/Str
                              (s/optional-key :aws-secret-key) s/Str
                              (s/optional-key :aws-access-key-id) s/Str
                              :ephemeral? s/Bool})
      (ctr/wrap-defaults {:ephemeral? false})
      (ctr/wrap-kargs)))

(s/defrecord DatomicConn
    [database
     connection]
  Lifecycle
  (start [this]
    (assoc this :connection
           (d/connect (get-in this [:database :uri]))))
  (stop [this]
    (some-> this
            :connection
            d/release)
    (assoc this :connection nil))
  DatabaseReference
  (as-db [this]
    (as-db (as-conn this)))
  DatomicConnection
  (as-conn [this]
    (:connection this)))

(def new-datomic-connection
  (-> map->DatomicConn
      (ctr/wrap-class-validation DatomicConn)
      (ctr/wrap-using [:database])
      (ctr/wrap-kargs)))

(s/defrecord DatomicNormsConformer
    [connection :- (s/protocol DatomicConnection)]
  Lifecycle
  (start [this]
    (let [norms-map (p/norms this)]
      (c/ensure-conforms (as-conn connection) norms-map (keys norms-map)))
    this)
  (stop [this] this)
  p/DatomicNorms
  (norms [this]
    (->> (vals this)
         (filter #(satisfies? p/DatomicNorms %))
         (map p/norms)
         (apply merge))))

(def new-datomic-norms-conformer
  (-> map->DatomicNormsConformer
      (ctr/wrap-class-validation DatomicNormsConformer)
      (ctr/wrap-using [:connection])
      (ctr/wrap-kargs)))

(s/defrecord DatomicNormsResource
    [resource :- java.net.URL
     norms :- [s/Keyword]]
  p/DatomicNorms
  (norms [_]
    (with-open [rdr (java.io.PushbackReader. (io/reader resource))]
      (binding [clojure.tools.reader/*data-readers*
                {'db/id datomic.db/id-literal
                 'db/fn datomic.function/construct
                 'base64 datomic.codec/base-64-literal}]
        (clojure.tools.reader/read (indexing-push-back-reader rdr))))))

(def new-datomic-norms-resource
  (-> map->DatomicNormsResource
      (ctr/wrap-class-validation DatomicNormsResource)
      (ctr/wrap-kargs)))

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

(def new-datomic-report-queue
  (-> map->DatomicReportQueue
      (ctr/wrap-class-validation DatomicReportQueue)
      (ctr/wrap-using [:connection])
      (ctr/wrap-kargs)))

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

(def new-datomic-tx-listener-aggregator
  (-> map->DatomicTXListenerAggregator
      (ctr/wrap-class-validation DatomicTXListenerAggregator)
      (ctr/wrap-using [:tx-report-queue])
      (ctr/wrap-kargs)))
