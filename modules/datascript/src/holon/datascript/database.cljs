(ns holon.datascript.database
  (:require [holon.datascript.protocols :refer (DatascriptConnection DatabaseReference DatascriptNorms DatascriptTXListener ReactiveDB as-db as-conn txes)]
            [datascript :as d]
            [datascript.core :refer [IDB]]
            [ib5k.component.ctr :as ctr]
            [plumbing.core :refer-macros [defnk fnk <-]]
            [quile.component :refer (Lifecycle)]
            [reagent.core :as r]
            [reagent.ratom :refer [IReactiveAtom]]
            [schema.core :as s :include-macros true]
            [shodan.console :as c :include-macros true])
  (:require-macros [reagent.ratom :refer [reaction run!]]))

(s/defschema Query
  (s/make-fn-schema [[s/Any]]
                    [[(s/one (s/both (s/protocol DatascriptConnection)
                                     (s/protocol DatabaseReference))
                             "db")]]))

;; ========== Lifecycle ==========

(defn clear-listeners! [db]
  (doseq [listener (:listeners (meta (as-conn db)))]
    (d/unlisten! (as-conn db) listener)))

(s/defrecord DatascriptDB
    [schema :- {s/Keyword {s/Keyword s/Keyword}}
     history :- (s/maybe (s/protocol IReactiveAtom))
     db-reference :- (s/maybe Atom)]
  Lifecycle
  (start [this]
    (let [db-reference (d/create-conn schema)
          history (r/atom [])]
      (d/listen! db-reference :history (fn [tx-report] (swap! history conj tx-report)))
      (assoc this
             :db-reference db-reference
             :history history)))
  (stop [this]
    (clear-listeners! this)
    this)
  DatascriptConnection
  (as-conn [this]
    db-reference)
  DatabaseReference
  (as-db [this]
    (as-db (as-conn this)))
  ReactiveDB
  (make-db-reaction [this f]
    (let [last-tx (reaction (peek @history))
          novelty (reaction (some-> @last-tx :tx-data f))
          result (r/atom (f this))]
      (run! (when (seq @novelty)
              (reset! result (f this))))
      result)))

(def new-datascript-db
  (-> map->DatascriptDB
      (ctr/wrap-class-validation DatascriptDB)
      (ctr/wrap-defaults {:schema {}})
      (ctr/wrap-kargs)))

(s/defrecord DatascriptNormsConformer
    [db :- (s/protocol DatascriptConnection)]
  Lifecycle
  (start [this]
    (let [txes (->> (for [[ckey v] this
                          :when (satisfies? DatascriptNorms v)]
                      (txes v))
                    (mapcat identity))]
      (doseq [tx txes]
        (d/transact! (as-conn db) tx))
      this))
  (stop [this]
    this))

(def new-datascript-norms-conformer
  (-> map->DatascriptNormsConformer
      (ctr/wrap-class-validation DatascriptNormsConformer)
      (ctr/wrap-using [:db])
      (ctr/wrap-kargs)))

(s/defrecord DatascriptNorms
    [txes]
  DatascriptNorms
  (txes [_]
    txes))

(def new-datascript-norms
  (-> map->DatascriptNorms
      (ctr/wrap-class-validation DatascriptNorms)
      (ctr/wrap-defaults {:txes []})
      (ctr/wrap-kargs)))

(s/defrecord DatascriptTXListenerAggregator
    [db :- (s/protocol DatascriptConnection)]
  Lifecycle
  (start [this]
    (let [handlers (->> (vals this)
                        (filter #(satisfies? DatascriptTXListener %))
                        (map tx-handler))
          handler (fn [& args]
                    (doseq [handler handlers]
                      (apply handler args)))]
      (d/listen! (as-conn db) :aggregator handler)
      this))
  (stop [this]
    (d/unlisten! (as-conn db) :aggregator)
    this))

(def new-datascript-tx-listener-aggregator
  (-> map->DatascriptTXListenerAggregator
      (ctr/wrap-class-validation DatascriptTXListenerAggregator)
      (ctr/wrap-using [:db])
      (ctr/wrap-kargs)))
