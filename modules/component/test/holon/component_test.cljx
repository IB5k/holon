(ns holon.component-test
  (:require [holon.component :as cmp]
            [holon.test
             :refer (#+clj with-system with-system-fixture *system*)
             #+cljs
             :refer-macros #+cljs (with-system)]
            [holon.component :refer (new-system start)]
            #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t]
            [ib5k.component.ctr :as ctr]
            [ib5k.component.using-schema :as us]
            [plumbing.core :refer [map-vals]]
            #+clj  [clojure.test :refer :all]
            #+clj  [schema.core :as s]
            #+cljs [schema.core :as s :include-macros true]
            [schema.test])
  #+cljs
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing test-var use-fixtures)]))

(use-fixtures :once schema.test/validate-schemas)

(defprotocol TestProtocol
  (get-key [this key]))

(s/defrecord TestComponent [test-key :- s/Keyword]
  TestProtocol
  (get-key [this key]
    (get this key)))

(s/defrecord TestUser [cmp *cmp])

(def components
  {:test
   {:cmp (->TestComponent :test)}
   :test-user
   {:cmp (map->TestUser {})
    :using {:cmp (s/protocol TestProtocol)}
    #+clj :co-using #+clj {:*cmp (s/protocol TestProtocol)}}})

(deftest extract-key-test
  (testing "pull key out of map"
    (is (= {:a 1}
           (cmp/extract-key {:a {:cmp 1}} :cmp))))
  (testing "remove entries where key is nil"
    (is (= {}
           (cmp/extract-key {:a {:cmp 1}} :using)))))
