(ns holon.datascript.database
  (:require [holon.datascript.protocols :refer (DatomicConnection DatabaseReference DatascriptNorms DatomListener ReactiveDB)]
            [datascript :as d]
            [ib5k.component.ctr :as ctr]
            [plumbing.core :refer-macros [defnk fnk <-]]
            [quile.component :refer (Lifecycle)]
            [schema.core :as s :include-macros true]))

;; ========== Lifecycle ==========

(defn listen-reactions! [db reactions-ref]
  (d/listen! (as-conn db)
             (fn [tx-data]
               (doseq [datom (:tx-data tx-data)
                       [path m] @reactions-ref
                       :let [fragments ((apply juxt path) datom) ]
                       [key callback] (get-in m fragments)]
                 (callback datom tx-data key)))))

(defn clear-listeners! [db]
  (doseq [listener (:listeners (meta (as-conn conn)))]
    (d/unlisten! (as-conn db) listener)))

(defn -bind
  ([conn f]
   (bind conn q (atom nil)))
  ([conn f state]
   (let [k (uuid/make-random-uuid)]
     (reset! state (f (as-db conn)))
     (d/listen! conn k (fn [tx-report]
                         (let [novelty (f (:tx-data tx-report))]
                           (when (seq novelty) ;; Only update if query results actually changed
                             (reset! state (f (:db-after tx-report)))))))
     (set! (.-__key state) k)
     state)))

(defn -unbind
  [conn state]
  (d/unlisten! conn (.-__key state)))

(s/defrecord DatascriptDB
    [schema :- {s/Keyword {s/Keyword s/Keyword}}
     reactions :- (s/maybe clojure.lang.Atom)
     db-reference :- (s/maybe clojure.lang.Atom)]
  Lifecycle
  (start [this]
    (let [db-reference (d/create-conn schema)
          reactions (atom {})]
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
  ReactiveDB
  (bind [db f]
    (-bind db f))
  (bind [db f state]
    (-bind db f state))
  (unbind [db key]
    (-unbind-query db key))
  DatomListener
  (listen-for! [this key path fragments callback]
    (swap! reactions assoc-in (concat [path] fragments [key]) callback))
  (unlisten-for! [this key path fragments]
    (swap! reactions update-in (concat [path] fragments) dissoc key)))

(def new-datascript-db
  (-> map->DatascriptDB
      (ctr/wrap-class-validation DatascriptDB)
      (ctr/wrap-kargs)))

(s/defrecord DatascriptNormsConformer
    [db :- (s/protocol DatabaseReference)]
  Lifecycle
  (start [this]
    (let [txes (->> (for [[ckey v] this
                          :when (satisfies? DatascriptFixtures v)]
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

(s/defrecord StaticDatascriptNorms
    [txes]
  DatascriptFixtures
  (txes [_]
    txes))

(def new-datascript-static-norms
  (-> map->StaticDatascriptNorms
      (ctr/wrap-class-validation StaticDatascriptNorms)
      (ctr/wrap-kargs)))

(s/defrecord DatascriptTXListenerAggregator
    [db :- (s/protocol DatabaseReference)
     key :- (s/maybe s/Str)]
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

(def new-datascript-norms-conformer
  (-> map->DatascriptTXListenerAggregator
      (ctr/wrap-class-validation DatascriptTXListenerAggregator)
      (ctr/wrap-using [:db])
      (ctr/wrap-kargs)))
