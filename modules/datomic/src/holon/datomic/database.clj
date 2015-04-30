(ns holon.datomic.database
  (:require [holon.datomic.protocols :as p]
            [holon.datomic.utils :refer :all]
            [datomic.api :as d]
            [com.stuartsierra.component :as component :refer (Lifecycle)]
            [juxt.datomic.extras :refer (DatabaseReference DatomicConnection as-conn as-db to-ref-id to-entity-map EntityReference)]
            [plumbing.core :refer :all]
            [schema.core :as s])
  (:import [datomic.peer Connection]))

(defnk make-datomic-uri
  [ephemeral? :- s/Bool
   uri :- s/Str
   db-name :- s/Str
   {aws-secret-key nil} :- s/Str
   {aws-access-key-id nil} :- s/Str
   :as opts]
  (cond-> (str uri db-name)
    ephemeral? (str "-" (uuid))
    (and aws-secret-key
         aws-access-key-id) (str (as-query-string (select-keys opts [:aws-secret-key :aws-access-key-id])))))

(s/defrecord EphemeralDatabase
    [uri :- s/Str]
  Lifecycle
  (start [this]
    (d/create-database uri)
    this)
  (stop [this]
    (d/delete-database uri)
    (d/shutdown false)
    this)
  p/DatomicDatabase
  (url [this]
    uri))

(s/defrecord DurableDatabase
    [uri :- s/Str]
  Lifecycle
  (start [this]
    (d/create-database uri)
    this)
  (stop [this]
    (d/shutdown false)
    this)
  p/DatomicDatabase
  (url [this]
    uri))

(s/defrecord DatomicConn
    [database :- (s/protocol p/DatomicDatabase)
     connection :- (s/maybe Connection)]
  Lifecycle
  (start [this]
    (assoc this :connection
           (d/connect (url database))))
  (stop [this]
    (some-> this
            :connection
            d/release)
    (assoc this :connection nil))
  DatabaseReference
  (as-db [this]
    (as-db (as-conn this)))
  DatomicConnection
  (as-conn [this]
    (:connection this)))
