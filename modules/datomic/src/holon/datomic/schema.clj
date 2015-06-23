(ns holon.datomic.schema
  (:require [schema.core :as s])
  (:import [datomic.db DbId]))

(s/defschema DatomicDatom
  [(s/one datomic.db.DbId "entity id")
   (s/one s/Keyword "attr")
   (s/one s/Any "value")
   (s/one s/Inst "time")])

(s/defschema DatomicTX
  [(s/one s/Keyword "db/fn")
   (s/one datomic.db.DbId "id")
   (s/one s/Keyword "attr")
   (s/one s/Any "value")])

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
