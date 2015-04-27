(ns holon.events-test
  (:require [holon.events :as events]
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

(defn components [config]
  {})

(defn new-test-system
  []
  (new-system (components {})))

(with-system (new-test-system))
