(ns holon.events
  (:require [holon.events.dispatcher :as d]
            [holon.events.protocols :as p]
            [holon.events.schema :refer (Event Lookup)]
            [ib5k.component.ctr :as ctr]
            #+clj  [schema.core :as s]
            #+cljs [schema.core :as s :include-macros true])
  (:import [holon.events.dispatcher EventHandlerAggregator]))

;; ========== EventProducer ===========

(def EventProducer p/EventProducer)

(s/defn events :- Lookup
  [component :- (s/protocol EventProducer)]
  (p/events component))

(s/defn new-event-producer :- (s/protocol EventProducer)
  [ctr :- (s/make-fn-schema [(s/protocol EventProducer)] [[s/Any]])
   & args :- [s/Any]]
  (apply ctr args))

;; ========== EventHandler ===========

(def EventHandler p/EventHandler)

(s/defn event-handlers :- Lookup
  [component :- (s/protocol EventHandler)]
  (p/event-handlers component))

(s/defn new-event-handler :- (s/protocol EventHandler)
  [ctr :- (s/make-fn-schema [(s/protocol EventHandler)] [[s/Any]])
   & args :- [s/Any]]
  (apply ctr args))

;; ========== Dispatcher ===========

(s/defn validate-events :- (s/protocol p/EventProducer)
  [component :- (s/protocol p/EventProducer)]
  (d/validate-events component))

(s/defn dispatch! :- s/Any
  [component :- (s/protocol p/EventProducer)
   event :- Event]
  (d/dispatch! component event))

(def new-event-handler-aggregator
  (-> d/map->EventHandlerAggregator
      (ctr/wrap-class-validation EventHandlerAggregator)
      (ctr/wrap-kargs)))
