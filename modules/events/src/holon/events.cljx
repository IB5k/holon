(ns holon.events
  (:require [holon.events.dispatcher :as d :refer [#+cljs EventHandlerAggregator]]
            [holon.events.protocols :as p]
            [holon.events.schema :refer (Event Lookup)]
            [ib5k.component.ctr :as ctr]
            #+clj  [schema.core :as s]
            #+cljs [schema.core :as s :include-macros true])
  #+clj
  (:import [holon.events.dispatcher EventHandlerAggregator]))

;; ========== EventProducer ===========

(s/defn event-schema :- Lookup
  [component :- (s/protocol p/EventProducer)]
  (p/event-schema component))

(s/defn new-event-producer :- (s/protocol p/EventProducer)
  [ctr :- (s/make-fn-schema [(s/protocol p/EventProducer)] [[s/Any]])
   & args :- [s/Any]]
  (apply ctr args))

;; ========== EventHandler ===========

(s/defn event-handlers :- Lookup
  [component :- (s/protocol p/EventHandler)]
  (p/event-handlers component))

(s/defn new-event-handler :- (s/protocol p/EventHandler)
  [ctr :- (s/make-fn-schema [(s/protocol p/EventHandler)] [[s/Any]])
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
