(ns holon.datomic
  (:require [holon.datomic.database :as db]
            [holon.datomic.norms :as norms]
            [holon.datomic.protocols :as p]
            [holon.datomic.schema :refer (DatomicSchema DatomicTX DatomicTXReport)]
            [ib5k.component.ctr :as ctr]
            [juxt.datomic.extras :refer (DatomicConnection)]
            [manifold.stream :as m]
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

(s/defn tx-stream :- (s/pred m/sourceable?)
  [component :- (s/protocol p/DatomicReportStream)]
  (p/tx-stream component))

;; ========== DatomicTXListener ==========

(s/defn tx-handler :- (s/make-fn-schema [[s/Any]] [[DatomicTXReport]])
  [component :- (s/protocol p/DatomicTXListener)]
  (p/tx-handler component))

(s/defn new-tx-handler :- (s/protocol p/DatomicTXListener)
  [ctr :- (s/make-fn-schema [(s/protocol p/DatomicTXListener)] [[s/Any]])
   & args :- [s/Any]]
  (apply ctr args))
