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

;; ========== DatomicNorms ==========

(s/defn norms :- {s/Keyword {:txes [[(s/either DatomicSchema
                                               DatomicTX)]]}}
  [component :- (s/protocol p/DatomicNorms)]
  (p/norms component))

;; ========== ListenDatomicReportQueue ==========

(s/defn tap-tx-queue! :- (s/protocol asyncp/ReadPort)
  [component :- (s/protocol p/ListenDatomicReportQueue)]
  (p/tap-tx-queue! component))

;; ========== DatomicTXListener ==========

(s/defn tx-handler :- (s/make-fn-schema [[s/Any]] [[{:db-before datomic.db.Db
                                                     :db-after datomic.db.Db
                                                     :tx-data [DatomicTX]}]])
  [component :- (s/protocol p/DatomicTXListener)]
  (p/tx-handler component))

(s/defn new-tx-handler :- (s/protocol p/DatomicTXListener)
  [ctr :- (s/make-fn-schema [(s/protocol p/DatomicTXListener)] [[s/Any]])
   & args :- [s/Any]]
  (apply ctr args))
