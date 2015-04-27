(ns holon.test
  (:require [#+clj  com.stuartsierra.component
             #+cljs quile.component
             :as component :refer [system-map system-using using]])
  #+cljs
  (:require-macros [holon.test]))

(def ^:dynamic *system* nil)

#+clj
(defn cljs-env?
  "Take the &env from a macro, and tell whether we are expanding into cljs."
  [env]
  (boolean (:ns env)))

#+clj
(defmacro if-cljs
  "Return then if we are generating cljs code and else for Clojure code.
   https://groups.google.com/d/msg/clojurescript/iBY5HaQda4A/w1lAQi9_AwsJ"
  [then else]
  (if (cljs-env? &env) then else))

#+clj
(defmacro with-system
  [system & body]
  `(let [start# (or (::start (meta ~system))
                    (if-cljs
                     'com.stuartsierra.component/start
                     'quile.component/start))
         s# (start# ~system)]
     (try
       (binding [*system* s#] ~@body)
       (finally
         (component/stop s#)))))

(defn with-system-fixture
  [system]
  (fn [f]
    (with-system (system)
      (f))))
