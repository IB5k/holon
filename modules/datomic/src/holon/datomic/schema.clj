(ns holon.datomic.schema
  (:require [schema.core :as s]
            [schema.macros :as macros]
            [schema.utils :as sutils])
  (:import [datomic.db DbId]))

(s/defschema DatomicDatom
  [(s/one datomic.db.DbId "entity id")
   (s/one s/Keyword "attr")
   (s/one s/Any "value")
   (s/one s/Inst "time")])

(s/defschema DatomicTX
  (s/either {:db/id s/Any
             s/Keyword s/Any}
            [(s/one s/Keyword "db/fn")
             (s/one datomic.db.DbId "id")
             (s/one s/Keyword "attr")
             (s/one s/Any "value")]))

(s/defschema DatomicTXReport
  {:db-before datomic.db.Db
   :db-after datomic.db.Db
   :tx-data [DatomicTX]})

(s/defschema DatomicSchema
  {:db/id DbId
   :db/ident s/Keyword
   :db/valueType (s/enum :db.type/keyword
                         :db.type/string
                         :db.type/boolean
                         :db.type/long
                         :db.type/float
                         :db.type/bigint
                         :db.type/double
                         :db.type/instant
                         :db.type/uuid
                         :db.type/uri)
   :db/cardinality (s/enum :db.cardinality/one
                           :db.cardinality/many)})

(s/defschema DatomicNorms
  {s/Keyword {:txes [[DatomicTX]]
              (s/optional-key :requires) [s/Keyword]}})

(s/defschema EntityLookup
  (s/either (s/named s/Num "entity id")
            [(s/one s/Keyword "attr")
             (s/one s/Any "unique value")]))

(defn entity?
  [e]
  (instance? datomic.Entity e))

;; Wrapper type needed because Entity values do not implement
;; IPersistentMap interface
(defrecord EntitySchema
  [schema]
  s/Schema
  (walker [this]
    (let [map-checker (s/subschema-walker schema)]
      (fn [e]
        (or (and (map? e) ;; allow entities that have already been realized as maps
                 (map-checker e))
            (when-not (entity? e)
              (macros/validation-error this e
                (list 'instance? datomic.Entity (sutils/value-name e))))
            (map-checker (into {} e))))))
  (explain [this]
    (list 'entity 'datomic.Entity (or (:schema this)
                                      (merge {} this)))))

(defn entity-schema?
  [schema]
  (= (type schema) EntitySchema))

(defn entity-schema
  [schema]
  (->EntitySchema schema))
