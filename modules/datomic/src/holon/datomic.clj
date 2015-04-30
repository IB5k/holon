(ns holon.datomic
  (:require [holon.datomic.database :as db]
            [holon.datomic.norms :as norms]
            [holon.datomic.protocols :as p]
            [holon.datomic.schema :refer (DatomicSchema DatomicTX)]
            [clojure.core.async.impl.protocols :as asyncp]
            [ib5k.component.ctr :as ctr]
            [juxt.datomic.extras :refer (DatomicConnection)]
            [schema.core :as s])
  (:import [holon.datomic.database EphemeralDatabase DurableDatabase]
           [holon.datomic.norms DatomicNormsConformer DatomicNormsResource]
           [holon.datomic.tx-listen DatomicReportQueue DatomicTXListenerAggregator]
           [datomic.db Db DbId]))

;; ========== DatomicDatabase ==========

(s/defn url :- s/Str
  [component :- (s/protocol p/DatomicDatabase)]
  (p/url component))

(def new-datomic-database
  (-> (fnk [ephemeral? :as opts]
        ((if ephemeral?
           db/map->EphemeralDatabase
           db/map->DurableDatabase)
         {:uri (db/make-datomic-uri opts)}))
      (ctr/wrap-class-validation DurableDatabase)
      (ctr/wrap-validation {:uri s/Str
                            :db-name s/Str
                            (s/optional-key :aws-secret-key) s/Str
                            (s/optional-key :aws-access-key-id) s/Str
                            :ephemeral? s/Bool})
      (ctr/wrap-defaults {:ephemeral? false})
      (ctr/wrap-kargs)))

(def new-datomic-connection
  (-> map->DatomicConn
      (ctr/wrap-class-validation DatomicConn)
      (ctr/wrap-using [:database])
      (ctr/wrap-kargs)))

;; ========== DatomicNorms ==========

(s/defn norms :- {s/Keyword {:txes [[(s/either DatomicSchema
                                               DatomicTX)]]}}
  [component :- (s/protocol p/DatomicNorms)]
  (p/norms component))

(def new-datomic-norms-conformer
  (-> norms/map->DatomicNormsConformer
      (ctr/wrap-class-validation DatomicNormsConformer)
      (ctr/wrap-using [:connection])
      (ctr/wrap-kargs)))

(def new-datomic-norms-resource
  (-> norms/map->DatomicNormsResource
      (ctr/wrap-class-validation DatomicNormsResource)
      (ctr/wrap-kargs)))

;; ========== ListenDatomicReportQueue ==========

(s/defn tap-tx-queue! :- (s/protocol asyncp/ReadPort)
  [component :- (s/protocol p/ListenDatomicReportQueue)]
  (p/tap-tx-queue! component))

(def new-datomic-report-queue
  (-> listen/map->DatomicReportQueue
      (ctr/wrap-class-validation DatomicReportQueue)
      (ctr/wrap-using [:connection])
      (ctr/wrap-kargs)))

;; ========== DatomicTXListener ==========

(s/defn tx-handler :- (s/make-fn-schema [[s/Any]] [[{:db-before datomic.db.Db
                                                     :db-after datomic.db.Db
                                                     :tx-data [DatomicTX]}]])
  [component :- (s/protocol p/DatomicTXListener)]
  (p/tx-handler component))

(def new-datomic-tx-listener-aggregator
  (-> listen/map->DatomicTXListenerAggregator
      (ctr/wrap-class-validation DatomicTXListenerAggregator)
      (ctr/wrap-using [:tx-report-queue])
      (ctr/wrap-kargs)))

(s/defn new-tx-handler :- (s/protocol p/DatomicTXListener)
  [ctr :- (s/make-fn-schema [(s/protocol p/DatomicTXListener)] [[s/Any]])
   & args :- [s/Any]]
  (apply ctr args))
