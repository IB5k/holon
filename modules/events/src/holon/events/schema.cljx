(ns holon.events.schema
  (:require #+clj  [schema.core :as s]
            #+cljs [schema.core :as s :include-macros true]))

(s/defschema EventKey
  (s/one s/Any "event key"))

(s/defschema Event
  [EventKey
   s/Any])

(s/defschema Lookup
  (s/either {s/Any [[s/Any]]}
            (s/make-fn-schema [[[s/Any]]] s/Any)))
