(ns holon.datascript.database
  (:require [datascript :as d]
            [plumbing.core :refer-macros [defnk fnk <-]]
            [quile.component :refer (Lifecycle)]
            [schema.core :as s :include-macros true]))

;; ========== Lifecycle ==========

(defn listen-reactions! [conn reactions-ref]
  (d/listen! (as-conn conn)
             (fn [tx-data]
               (doseq [datom (:tx-data tx-data)
                       [path m] @reactions-ref
                       :let [fragments ((apply juxt path) datom) ]
                       [key callback] (get-in m fragments)]
                 (callback datom tx-data key)))))

(defn clear-listeners! [conn]
  (doseq [listener (:listeners (meta (as-conn conn)))]
    (d/unlisten! (as-conn db) listener)))

(defn -bind-query
  [db key q inputs callback]
  (d/listen! (as-conn db) (prn-str key)
             (fn [{:keys [tx-data db-before db-after] :as tx}]
               (let [novelty (apply d/q q tx-data inputs)]
                 (when (seq novelty)
                   (callback tx)))))
  key)

(defn -unbind-query
  [conn key]
  (d/unlisten! (as-conn conn) (prn-str key)))

(defrecord DatascriptDB [schema db-reference reactions]
  Lifecycle
  (start [this]
    (validate-cmp this)
    (let [db-reference (d/create-conn schema)
          reactions (atom {})
          txes (->> (for [[ckey v] this
                          :when (satisfies? DatascriptFixtures v)]
                      (txes v))
                    (mapcat identity))]
      (doseq [tx txes]
        (d/transact! db-reference tx))
      (listen-reactions! db-reference reactions)
      (assoc this
             :db-reference db-reference
             :reactions reactions)))
  (stop [this]
    (clear-listeners! this)
    (reset! reactions {})
    this)
  DatomicConnection
  (as-conn [this]
    db-reference)
  DatabaseReference
  (as-db [this]
    (as-db (as-conn this)))
  TXListener
  (bind-query [db key q inputs callback]
    (-bind-query db key q inputs callback))
  (unbind-query [db key]
    (-unbind-query db key))
  (reactive-q [db key query inputs]
    (-react-q db key query inputs))
  (reactive-q [db key query]
    (reactive-q db key query []))
  DatomListener
  (listen-for! [this key path fragments callback]
    (swap! reactions assoc-in (concat [path] fragments [key]) callback))
  (unlisten-for! [this key path fragments]
    (swap! reactions update-in (concat [path] fragments) dissoc key)))

(defrecord DatascriptDB [schema db-reference reactions]
  Lifecycle
  (start [this]
    (validate-cmp this)
    (let [db-reference (d/create-conn schema)
          reactions (atom {})
          txes (->> (for [[ckey v] this
                          :when (satisfies? DatascriptFixtures v)]
                      (txes v))
                    (mapcat identity))]
      (doseq [tx txes]
        (d/transact! db-reference tx))
      (listen-reactions! db-reference reactions)
      (assoc this
             :db-reference db-reference
             :reactions reactions)))
  (stop [this]
    (clear-listeners! this)
    (reset! reactions {})
    this)
  DatomicConnection
  (as-conn [this]
    db-reference)
  DatabaseReference
  (as-db [this]
    (as-db (as-conn this)))
  TXListener
  (bind-query [db key q inputs callback]
    (-bind-query db key q inputs callback))
  (unbind-query [db key]
    (-unbind-query db key))
  (reactive-q [db key query inputs]
    (-react-q db key query inputs))
  (reactive-q [db key query]
    (reactive-q db key query []))
  DatomListener
  (listen-for! [this key path fragments callback]
    (swap! reactions assoc-in (concat [path] fragments [key]) callback))
  (unlisten-for! [this key path fragments]
    (swap! reactions update-in (concat [path] fragments) dissoc key)))

(def new-datascript-db
  (validated-ctr
   {:map->cmp map->DatascriptDB
    :opts {:schema [(s/maybe {s/Any s/Any})
                    {}]}}))

(defrecord StaticDatascriptFixtures [txes]
  Lifecycle
  (start [this]
    (validate-cmp this)
    this)
  (stop [this]
    this)
  DatascriptFixtures
  (txes [_]
    txes))

(def new-datascript-fixtures
  (validated-ctr
   {:map->cmp map->StaticDatascriptFixtures
    :opts {:txes [[[{s/Keyword s/Any}]]
                  []]}}))

(defprotocol DatascriptTXListener
  (tx-handler [_]))

(defrecord DatascriptTXListenerAggregator [db key]
  Lifecycle
  (start [this]
    (validate-cmp this)
    (let [handlers (->> (vals this)
                        (filter #(satisfies? DatascriptTXListener %))
                        (map tx-handler))
          handler (fn [& args]
                    (doseq [handler handlers]
                      (apply handler args)))
          key (uuid)]
      (d/listen! (as-conn db) key handler)
      (assoc this
             :key key)))
  (stop [this]
    (unbind-query db key)
    this))

(def new-datascript-tx-listener-aggregator
  (validated-ctr
   {:map->cmp map->DatascriptTXListenerAggregator
    :using {:db (s/protocol DatabaseReference)}}))
