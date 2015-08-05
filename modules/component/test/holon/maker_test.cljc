(ns holon.maker-test
  (:require [holon.maker :refer (make-args make)]
            #?@
            (:clj
             [[clojure.test :refer :all]]
             :cljs
             [[cemerick.cljs.test :as t]])
            [schema.core :as s #?@(:cljs [:include-macros true])]
            [schema.test])
  #?(:cljs
     (:require-macros [cemerick.cljs.test
                       :refer (is deftest with-test run-tests testing test-var use-fixtures)])))

(use-fixtures :once schema.test/validate-schemas)

(deftest make-args-test
  (testing "Default if no override"
    (is (= '(:a 1) (make-args {} :a 1))))
  (testing "No specified value, no arg"
    (is (= '() (make-args {:a 2}))))
  (testing "Config overrides specified value"
    (is (= '(:a 2) (make-args {:a 2} :a 1))))
  (testing "Config overrides specified value: false still wins"
    (is (= '(:a false) (make-args {:a false} :a 1))))
  (testing "Nil selects config"
    (is (= '(:a 2) (make-args {:a 2} :a nil))))
  (testing "Nil selects config"
    (is (= '(:a 2) (make-args {:a 2} :a :required))))
  (testing "Exception thrown on required"
    (is (thrown? #?(:clj Throwable :cljs js/Error)
                 (make-args {} :a :holon.maker/required))))
  (testing "Mapping with a keyword"
    (is (= '(:a 2) (make-args {:b 2} {:a :b} 1))))
  (testing "Mapping with a vector path"
    (is (= '(:a 2) (make-args {:b {:c 2}} {:a [:b :c]} 1)))))
