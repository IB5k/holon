(ns holon.datomic.norms
  (:require [holon.datomic.protocols :as p]
            [holon.datomic.utils :refer :all]
            [datomic.api :as d]
            [clojure.java.io :as io]
            [clojure.tools.reader]
            [clojure.tools.reader.reader-types :refer (indexing-push-back-reader)]
            [com.stuartsierra.component :as component :refer (Lifecycle)]
            [io.rkn.conformity :as c]
            [juxt.datomic.extras :refer (DatabaseReference DatomicConnection as-conn as-db to-ref-id to-entity-map EntityReference)]
            [plumbing.core :refer :all]
            [schema.core :as s]))

(s/defrecord DatomicNormsConformer
    [connection :- (s/protocol DatomicConnection)]
  Lifecycle
  (start [this]
    (let [norms-map (p/norms this)]
      (c/ensure-conforms (as-conn connection) norms-map (keys norms-map)))
    this)
  (stop [this] this)
  p/DatomicNorms
  (norms [this]
    (->> (vals this)
         (filter #(satisfies? p/DatomicNorms %))
         (map p/norms)
         (apply merge))))

(s/defrecord DatomicNormsResource
    [resource :- java.net.URL
     norms :- [s/Keyword]]
  p/DatomicNorms
  (norms [_]
    (with-open [rdr (java.io.PushbackReader. (io/reader resource))]
      (binding [clojure.tools.reader/*data-readers*
                {'db/id datomic.db/id-literal
                 'db/fn datomic.function/construct
                 'base64 datomic.codec/base-64-literal}]
        (clojure.tools.reader/read (indexing-push-back-reader rdr))))))
