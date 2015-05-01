(ns holon.datascript
  (:require [datascript :as d]
            [goog.string.StringBuffer]
            [plumbing.core :refer-macros [defnk fnk <-]]
            [quile.component :refer (Lifecycle)]
            [schema.core :as s :include-macros true]))

;; ========== Protocols ==========

(defn qatom [db key query & inputs]
  (reactive-q db key query inputs))

;; ========== Helpers ==========

(defn entity-exists? [db id]
  (seq (d/datoms (as-db db) :eavt (to-ref-id id))))

(defn uuid
  "Returns a new randomly generated (version 4) cljs.core/UUID,
  like: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
  as per http://www.ietf.org/rfc/rfc4122.txt.
  Usage:
  (make-random)  =>  #uuid \"305e764d-b451-47ae-a90d-5db782ac1f2e\"
  (type (make-random)) => cljs.core/UUID"
  []
  (letfn [(f [] (.toString (rand-int 16) 16))
          (g [] (.toString  (bit-or 0x8 (bit-and 0x3 (rand-int 15))) 16))]
    (UUID. (.toString
            (goog.string.StringBuffer.
             (f) (f) (f) (f) (f) (f) (f) (f) "-" (f) (f) (f) (f)
             "-4" (f) (f) (f) "-" (g) (f) (f) (f) "-"
             (f) (f) (f) (f) (f) (f) (f) (f) (f) (f) (f) (f))))))

;; ========== Lifecycle ==========

(defn listen-reactions! [conn reactions-ref]
  (d/listen! conn
             (fn [tx-data]
               (doseq [datom (:tx-data tx-data)
                       [path m] @reactions-ref
                       :let [fragments ((apply juxt path) datom) ]
                       [key callback] (get-in m fragments)]
                 (callback datom tx-data key)))))

(defn clear-listeners! [db]
  (doseq [listener (:listeners (meta (as-conn db)))]
    (d/unlisten! (as-conn db) listener)))

(defn same-entities? [{:keys [tx-data db-before db-after]} q inputs]
  (->> (apply d/q q tx-data inputs)
       (mapcat identity)
       (mapv (juxt (partial entity-exists? db-before)
                   (partial entity-exists? db-after)))
       (mapv (partial apply =))
       (reduce #(and %1 %2))))

(defn -bind-query
  [db key q inputs callback]
  (d/listen! (as-conn db) (prn-str key)
             (fn [{:keys [tx-data db-before db-after] :as tx}]
               (let [novelty (apply d/q q tx-data inputs)]
                 (when (seq novelty)
                   (callback tx)))))
  key)

(defn -unbind-query
  [db key]
  (d/unlisten! (as-conn db) (prn-str key)))

;; ========== Mixins ==========

(defn ids->entities [db]
  "mixin that wraps render to transform passed eids to entities"
  {:wrap-render
   (fn [render-fn]
     (fn [state]
       (let [eids (:rum/args state)
             entities (mapv (partial d/entity (as-db db)) eids)
             [dom next-state] (render-fn (assoc state :rum/args entities))]
         [dom (assoc next-state :rum/args eids)])))})

(def *queries* (atom nil))

(defn query-reactive [db]
  "a mixin that creates reactions to (query) calls inside components"
  {:transfer-state
   (fn [old new]
     (assoc new ::queries (::queries old)))
   :wrap-render
   (fn [render-fn]
     (fn [state]
       (reset! *queries* (::queries state {}))
       (let [comp             (:rum/react-component state)
             old-queries      (::queries state {})
             [dom next-state] (render-fn state)
             new-queries      @*queries*]
         (doseq [[key _] old-queries]
           (when-not (get new-queries key)
             (unbind-query db key)))
         (doseq [[key [query inputs ref]] new-queries]
           (when-not (get old-queries [query inputs])
             (bind-query db key query inputs
                         (fn [{:keys [db-after]}]
                           (reset! ref (->> inputs
                                            (apply d/q query db-after)
                                            (mapcat identity)))))))
         [dom (assoc next-state ::queries new-queries)])))
   :will-unmount
   (fn [state]
     (doseq [[key _] (::queries state)]
       (unbind-query db key))
     (dissoc state ::queries))})

(defn -react-q [db key query inputs]
  (let [qatom (or (get @*queries* [query inputs])
                  (->> inputs
                       (apply d/q query (as-db db))
                       atom))]
    (swap! *queries* assoc key [query inputs qatom])
    qatom))

(defn listen-for-mixin [db path-fn]
  "Reusable mixin that subscribes to the part of DB"
  {:will-mount
   (fn [state]
     (let [comp  (:rum/react-component state)
           paths (doall
                  (for [eid (:rum/args state)]
                    (let [[args path] (apply path-fn (:rum/args state))
                          key         (rand)
                          callback    (fn [datom tx-data key]
                                        (rum/request-render comp))]
                      (listen-for! db key args path callback)
                      [key args path])))]
       (assoc state
              ::listen-path paths)))
   :will-unmount
   (fn [state]
     (doseq [path (::listen-paths state)]
       (apply unlisten-for! path)))})

;; ========== Components ==========

(defprotocol DatascriptFixtures
  (txes [_]))

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
