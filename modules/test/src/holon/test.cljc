(ns holon.test
  (:require [#?(:clj
                com.stuartsierra.component
                :cljs
                quile.component)
             :as component :refer [system-map #?(:cljs SystemMap)]])
  #?(:cljs
     (:require-macros [holon.test :refer (with-system)])))

(def ^:dynamic *system* nil)

#?(:clj
   (do
     (defn cljs-env?
       "Take the &env from a macro, and tell whether we are expanding into cljs."
       [env]
       (boolean (:ns env)))

     (defmacro if-cljs
       "Return then if we are generating cljs code and else for Clojure code.
   https://groups.google.com/d/msg/clojurescript/iBY5HaQda4A/w1lAQi9_AwsJ"
       [then else]
       (if (cljs-env? &env) then else))

     (defmacro with-system
       [system & body]
       `(let [start# (or (::start (meta ~system))
                         (if-cljs
                          ~'quile.component/start
                          ~'com.stuartsierra.component/start))
              stop# (if-cljs
                     ~'quile.component/stop
                     ~'com.stuartsierra.component/stop)
              s# (start# ~system)]
          (try
            (binding [*system* s#] ~@body)
            (finally
              (stop# s#)))))))

(defn with-system-fixture
  [system]
  (fn [f]
    (with-system (system)
      (f))))
