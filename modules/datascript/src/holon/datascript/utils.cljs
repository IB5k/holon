(ns holon.datascript.utils
  (:require [datascript :as d]
            [goog.string.StringBuffer]
            [schema.core :as s :include-macros true]))

(defn entity-exists? [db id]
  (seq (d/datoms (as-db db) :eavt (to-ref-id id))))

(defn uuid
  "Returns a new randomly generated (version 4) cljs.core/UUID,
  like: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
  as per http://www.ietf.org/rfc/rfc4122.txt.
  Usage:
  (make-random)  =>  #uuid \"305e764d-b451-47ae-a90d-5db782ac1f2e\"
  (type (make-random)) => cljs.core/UUID"
  []
  (letfn [(f [] (.toString (rand-int 16) 16))
          (g [] (.toString  (bit-or 0x8 (bit-and 0x3 (rand-int 15))) 16))]
    (UUID. (.toString
            (goog.string.StringBuffer.
             (f) (f) (f) (f) (f) (f) (f) (f) "-" (f) (f) (f) (f)
             "-4" (f) (f) (f) "-" (g) (f) (f) (f) "-"
             (f) (f) (f) (f) (f) (f) (f) (f) (f) (f) (f) (f))))))
