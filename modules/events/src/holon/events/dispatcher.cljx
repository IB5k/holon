(ns holon.events.dispatcher
  (:require [holon.events.protocols :as p]
            [holon.events.schema :refer (Lookup)]
            [#+clj  com.stuartsierra.component
             #+cljs quile.component
             :as component :refer [Lifecycle]]
            [ib5k.component.ctr :as ctr]
            #+clj  [schema.core :as s]
            #+cljs [schema.core :as s :include-macros true]))

(defn validate-events [cmp]
  (assert (satisfies? p/EventProducer cmp) "cmp must satisfy EventProducer for events to be validated")
  (s/validate Lookup (p/events cmp))
  (let [bus (first (filter #(satisfies? p/EventHandler %) (vals cmp)))]
    (assert bus "component must depend on an event bus to validate-events")
    (doseq [[event arities] (p/events cmp)]
      (when-not (seq (filter #(% event) (p/event-handlers bus)))
        (throw (ex-info (str "event handler missing for event: " event)
                        {:event event
                         :arities arities
                         :component cmp})))))
  cmp)

(defn dispatch! [cmp [event & args]]
  (when-not (satisfies? p/EventProducer cmp)
    (throw (ex-info "component must satisfy EventProducer to dispatch!"
                    {:component cmp
                     :event event
                     :args args})))
  (if-let [bus (first (filter #(satisfies? p/EventHandler %) (vals cmp)))]
    (let [schema (->> (p/events cmp)
                      (map #(% event)))]
      (when (seq schema)
        (when (< 1 (count schema))
          (throw (ex-info (str "ambigious schema defined in EventProducer.events for event: " event)
                          {:component cmp
                           :event event
                           :args args
                           :schema schema})))
        (s/validate (first schema) args))
      (doseq [matcher (p/event-handlers bus)
              :let [handler (matcher event)]
              :when handler]
        (apply handler args)))
    (throw (ex-info "component must depend on an event bus to dispatch!"
                    {:component cmp
                     :event event
                     :args args}))))

(defrecord EventHandlerAggregator []
  Lifecycle
  (start [this]
    (let [handlers (->> (vals this)
                        (filter #(satisfies? p/EventHandler %))
                        (mapcat p/event-handlers))]
      (assoc this :handlers handlers)))
  (stop [this] this)
  p/EventHandler
  (event-handlers [this] (:handlers this)))

(def new-event-handler-aggregator
  (-> map->EventHandlerAggregator
      (ctr/wrap-class-validation EventHandlerAggregator)
      (ctr/wrap-kargs)))
