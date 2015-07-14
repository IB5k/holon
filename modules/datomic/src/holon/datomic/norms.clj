(ns holon.datomic.norms
  (:require [holon.datomic.protocols :as p]
            [holon.datomic.utils :refer :all]
            [holon.datomic.schema :refer :all]
            [clojure.java.io :as io]
            [clojure.tools.reader]
            [clojure.tools.reader.reader-types :refer (indexing-push-back-reader)]
            [com.stuartsierra.component :as component :refer (Lifecycle)]
            [datomic.api :as d]
            [ib5k.component.ctr :as ctr]
            [io.rkn.conformity :as c]
            [juxt.datomic.extras :refer (DatabaseReference DatomicConnection as-conn as-db to-ref-id to-entity-map EntityReference)]
            [plumbing.core :refer :all]
            [schema.core :as s]))

(s/defrecord DatomicNormsConformer
    [connection :- (s/protocol DatomicConnection)
     norms :- (s/maybe [s/Keyword])]
  Lifecycle
  (start [this]
    (let [norms-map (p/norms this)]
      (c/ensure-conforms (as-conn connection) norms-map (or norms (keys norms-map))))
    this)
  (stop [this] this)
  p/DatomicNorms
  (norms [this]
    (->> (vals this)
         (filter #(satisfies? p/DatomicNorms %))
         (mapv p/norms)
         (apply merge))))

(def new-datomic-norms-conformer
  (-> map->DatomicNormsConformer
      (ctr/wrap-class-validation DatomicNormsConformer)
      (ctr/wrap-using [:connection])
      (ctr/wrap-kargs)))

(s/defrecord DatomicNorms
    [key :- s/Keyword
     requires :- [s/Keyword]
     txes :- [[DatomicTX]]]
  p/DatomicNorms
  (norms [_]
    {key {:requires requires
          :txes txes}}))

(def new-datomic-norms
  (-> map->DatomicNorms
      (ctr/wrap-class-validation DatomicNorms)
      (ctr/wrap-kargs)))

(s/defrecord DatomicNormsResource
    [resource :- s/Str]
  p/DatomicNorms
  (norms [_]
    (with-open [rdr (java.io.PushbackReader. (io/reader (io/resource resource)))]
      (binding [clojure.tools.reader/*data-readers*
                {'db/id datomic.db/id-literal
                 'db/fn datomic.function/construct
                 'base64 datomic.codec/base-64-literal}]
        (clojure.tools.reader/read (indexing-push-back-reader rdr))))))

(def new-datomic-norms-resource
  (-> map->DatomicNormsResource
      (ctr/wrap-class-validation DatomicNormsResource)
      (ctr/wrap-kargs)))
