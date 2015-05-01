(ns holon.datascript.protocols)

(defprotocol DatomicConnection
  (as-conn [_]))

(extend-protocol DatomicConnection
  cljs.core.Atom
  (as-conn [c] c))

(defprotocol DatabaseReference
  (as-db [_]))

(extend-protocol DatabaseReference
  datascript.core.DB
  (as-db [db] db)
  cljs.core.PersistentVector
  (as-db [db] db)
  cljs.core.Atom
  (as-db [db] @db))

(defprotocol EntityReference
  (to-ref-id [_])
  (to-entity-map [_ db]))

(extend-protocol EntityReference
  datascript.impl.entity.Entity
  (to-ref-id [em] (:db/id em))
  (to-entity-map [em _] em)
  number
  (to-ref-id [id] id)
  (to-entity-map [id db] (d/entity (as-db db) id))
  cljs.core.Keyword
  (to-ref-id [k] k)
  (to-entity-map [k db] (d/entity (as-db db) k))
  string
  (to-ref-id [id] (to-ref-id (.parseInt js/window id)))
  (to-entity-map [id db] (to-entity-map (to-ref-id id) db))
  cljs.core.PersistentVector
  (to-ref-id [em] (first em))
  (to-entity-map [id db] (to-entity-map (first id) db)))

(defprotocol DatascriptNorms
  (txes [_]))

(defprotocol DatomListener
  (listen-for! [db key path fragments callback])
  (unlisten-for! [db key path fragments]))

(defprotocol TXListener
  (bind-query [db key q inputs callback])
  (unbind-query [db key])
  ( [db key query] [db key query inputs]))
