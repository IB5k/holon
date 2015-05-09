(ns holon.events.dispatcher
  (:require [holon.events.protocols :as p]
            [holon.events.schema :refer (EventKey Event Lookup)]
            [#+clj  com.stuartsierra.component
             #+cljs quile.component
             :as component :refer [Lifecycle]]
            #+clj  [schema.core :as s]
            #+cljs [schema.core :as s :include-macros true]))

(s/defn lookup
  [key :- EventKey
   lookups :- [Lookup]]
  (filter #(% key) lookups))

(s/defn get-event-handlers
  [cmp :- (s/protocol p/EventProducer)]
  (->> (vals cmp)
       (filter #(satisfies? p/EventHandler %))
       (mapcat p/event-handlers)))

(s/defn validate-events :- (s/protocol p/EventProducer)
  [cmp :- (s/protocol p/EventProducer)]
  (s/validate Lookup (p/event-schema cmp))
  (let [handlers (get-event-handlers cmp)]
    (assert (seq handlers)
            "EventProducers must depend on component(s) that satisfies EventHandler")
    (doseq [[key arities] (p/event-schema cmp)]
      (when-not (seq (lookup key handlers))
        (throw (ex-info (str "event handler missing for event: " event)
                        {:event-key key
                         :arities arities
                         :component cmp}))))z
    cmp))

(s/defn get-event-schema
  [cmp :- (s/protocol p/EventProducer)
   key :- EventKey]
  (let [event-schema (->> (p/event-schema cmp)
                          (filter #(% event)))
        info {:component cmp
              :event event
              :event-schema event-schema}]
    (if (seq event-schema)
      (if (< 1 (count event-schema))
        (throw (ex-info (str "ambigious event-schema defined for event: " event) info))
        (first event-schema))
      (throw (ex-info (str "no event-schema defined for event: " event) info)))))

(s/defn handle
  [cmp :- (s/protocol p/EventProducer)
   [key & args] :- Event])

(s/defn dispatch!
  [cmp :- (s/protocol p/EventProducer)
   [key & args] :- Event]
  (let [handlers (get-event-handlers cmp)]
    (s/validate (get-event-schema cmp) args)
    (doseq [matcher (p/event-handlers bus)
            :let [handler (matcher key)]
            :when handler]
      (apply handler args))))

(defrecord EventHandlerAggregator []
  Lifecycle
  (start [this]
    (assoc this :handlers (get-event-handlers this)))
  (stop [this] this)
  p/EventHandler
  (event-handlers [this] (:handlers this)))

(def new-event-handler-aggregator
  (-> map->EventHandlerAggregator
      (ctr/wrap-class-validation EventHandlerAggregator)
      (ctr/wrap-kargs)))
