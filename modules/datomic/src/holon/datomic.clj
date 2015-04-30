(ns holon.datomic
  (:require [holon.datomic.database :as db]
            [holon.datomic.protocols :as p]
            [clojure.core.async.impl.protocols :as asyncp]
            [ib5k.component.ctr :as ctr]
            [juxt.datomic.extras :refer (DatomicConnection)]
            [schema.core :as s])
  (:import [datomic.db Db DbId]))

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

(s/defn -new-datomic-database :- (s/protocol p/DatomicDatabase)
  [opts :- {:uri s/Str
            :db-name s/Str
            (s/optional-key :aws-secret-key) s/Str
            (s/optional-key :aws-access-key-id) s/Str
            :ephemeral? s/Bool}]
  (db/new-datomic-database opts))
(def new-datomic-database (ctr/wrap-kargs -new-datomic-database))

(s/defn new-datomic-connection :- (s/protocol DatomicConnection)
  []
  (db/new-datomic-connection))

(s/defn new-datomic-norms-conformer :- (s/protocol p/DatomicNorms)
  []
  (db/new-datomic-norms-conformer))

(s/defn -new-datomic-norms-resource :- (s/protocol p/DatomicNorms)
  [opts :- {:resource java.net.URL
            :norms [s/Keyword]}]
  (db/new-datomic-norms-resource opts))
(def new-datomic-norms-resource (ctr/wrap-kargs -new-datomic-norms-resource))

(s/defn new-datomic-report-queue :- (s/protocol p/ListenDatomicReportQueue)
  []
  (db/new-datomic-report-queue))

(s/defn new-datomic-tx-listener-aggregator :- (s/protocol p/DatomicTXListener)
  []
  (db/new-datomic-tx-listener-aggregator))
