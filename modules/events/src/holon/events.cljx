(ns holon.events
  (:require [holon.events.dispatcher :as d]
            [holon.events.protocols :as p]
            [holon.events.schema :refer (Event Lookup)]
            [ib5k.component.ctr :as ctr]
            #+clj  [schema.core :as s]
            #+cljs [schema.core :as s :include-macros true]))

(s/defn events :- Lookup
  [component :- (s/protocol p/EventProducer)]
  (p/events component))

(s/defn event-handlers :- Lookup
  [component :- (s/protocol p/EventHandler)]
  (p/event-handlers component))

(s/defn validate-events :- (s/protocol p/EventProducer)
  [component :- (s/protocol p/EventProducer)]
  (d/validate-events component))

(s/defn dispatch! :- s/Any
  [component :- (s/protocol p/EventProducer)
   event :- Event]
  (d/dispatch! component event))
