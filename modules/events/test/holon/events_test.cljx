(ns holon.events-test
  (:require [holon.events :as events]
            [holon.events.protocols :as p]
            [holon.test
             :refer (#+clj with-system with-system-fixture *system*)
             #+cljs
             :refer-macros #+cljs (with-system)]
            [holon.component :refer (new-system start)]
            #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t]
            [ib5k.component.ctr :as ctr]
            #+clj  [clojure.test :refer :all]
            #+clj  [schema.core :as s]
            #+cljs [schema.core :as s :include-macros true]
            [schema.test])
  #+cljs
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing test-var use-fixtures)]))

(use-fixtures :once schema.test/validate-schemas)

(defrecord TestHandler []
  p/EventHandler
  (event-handlers [_]
    {:submit-number (fn [n]
                      n)}))

(defrecord TestProducer []
  p/EventProducer
  (events [_]
    {:submit-number [s/Num]}))

(def components
  {:event-dispatcher
   {:cmp (events/new-event-handler-aggregator)
    :using [(s/protocol p/EventHandler)]}
   :handler
   {:cmp (events/new-event-producer ->TestHandler)}
   :producer
   {:cmp (events/new-event-producer ->TestProducer)}})

(defn new-test-system
  []
  (-> components
      new-system
      (with-meta {:holon.test/start start})))

(deftest dispatcher
  (is
   (with-system (new-test-system)
     (:handler (:event-dispatcher *system*)))))
