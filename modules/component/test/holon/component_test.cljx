(ns holon.component-test
  (:require [holon.component :as cmp]
            [holon.test
             :refer (#+clj with-system with-system-fixture *system*)
             #+cljs
             :refer-macros #+cljs (with-system)]
            [holon.component :refer (new-system start)]
            #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t]
            [#+clj  com.stuartsierra.component
             #+cljs quile.component
             :as component :refer [Lifecycle system-map]]
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

(s/defrecord TestUser [cmp :- (s/protocol TestProtocol)
                       *cmp started?]
  Lifecycle
  (start [this]
    (assoc this :started? true))
  (stop [this]
    (assoc this :started? nil)))

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

(deftest new-system-test
  (is (new-system components))
  (is (= {:com.stuartsierra.component/dependencies {:cmp :test},
          :tangrammer.component.co-dependency/co-dependencies {:*cmp :test}}
         (meta (:test-user (new-system components))))))

(deftest expand-test
  (testing "expand calls functions before or after system start"
    (is
     (with-system (with-meta (new-system components)
                    {:holon.test/start
                     (fn [system-map]
                       (cmp/expand system-map
                                   {:before-start [[#(assoc % :has-started? (:started %))]]}))})
       (nil? (:has-started? (:test-user *system*)))))
    (is
     (with-system (with-meta (new-system components)
                    {:holon.test/start
                     (fn [system-map]
                       (cmp/expand system-map
                                   {:after-start [[#(assoc % :has-started? (:started? %))]]}))})
       (:has-started? (:test-user *system*)))))
  (testing "expand takes optional appended args"
    (is
     (with-system (with-meta (new-system components)
                    {:holon.test/start
                     (fn [system-map]
                       (cmp/expand system-map
                                   {:after-start [[(fn [cmp arg]
                                                     (assoc cmp :arg arg)) :test]]}))})
       (= :test (:arg (:test-user *system*)))))))

(deftest start-test
  #+clj
  (testing "start associates co-dependencies"
    (is
     (with-system (with-meta (new-system components)
                    {:holon.test/start cmp/start})
       (instance? TestComponent
                  @(:*cmp (:test-user *system*))))))

  (testing "start validates class schema"
    (is
     (thrown? #+clj Exception #+cljs js/Error
              (with-system (with-meta (system-map :test (->TestComponent "not a key"))
                             {:holon.test/start cmp/start})
                *system*)))))
