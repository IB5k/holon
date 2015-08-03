(ns holon.datomic.utils
  (:require [holon.datomic.schema :refer :all]
            [clojure.set :as set]
            [clojure.string :as str]
            [plumbing.core :refer :all]
            [schema.core :as s]
            [datomic.api :as d]
            [juxt.datomic.extras :refer (DatomicConnection DatabaseReference EntityReference as-conn as-db to-ref-id to-entity-map)]))

(defn uuid [] (str (java.util.UUID/randomUUID)))

;; ========== Keyword Manipulation ==========

(defn set-key-ns
  [ns key]
  (let [ns (cond-> ns
             (keyword? ns)
             (->> ((juxt namespace name))
                  (reduce #(or %1 %2))))]
    (keyword (when-not (str/blank? ns) ns) (name key))))

(defn split-keyword
  "split namespaced keyword into parts"
  [key]
  (->> key
       ((juxt namespace name))
       (mapcat #(some-> % (str/split #"\.")))
       (map keyword)))

(defn ns-key-from-seq [ks]
  (->> ks
       (map name)
       ((juxt (comp #(str/join "." %) butlast)
              (comp keyword last)))
       (apply set-key-ns)))

(defn split-ns-key-at [n key]
  (let [parts (split-keyword key)
        [nsks ks] (split-at (inc n) parts)
        ns-key  (ns-key-from-seq nsks)]
    (cond-> ns-key
      ns-key (cons ks))))

;; ========== Map Manipulation ==========

(defn namespaced-flatten-nested-map
  "returns a new map where keys are namespaced instead of nested"
  [m]
  (let [[non-maps maps] ((juxt (partial filter (comp not map? second))
                              (partial filter (comp map? second)))
                  (rest (tree-seq map? seq m)))]
    (into {} (apply concat non-maps
                    (when (seq maps)
                      (for [[k v] maps]
                        (let [ns (str/join "." ((juxt namespace name) k))]
                          (map-keys (partial set-key-ns ns) (namespaced-flatten-nested-map v)))))))))

(defn nest-namespaced-map
  "reverses the above"
  ([m] (nest-namespaced-map m 1))
  ([m ns-parts]
   (reduce-kv (fn [m k v]
                (let [parts (split-keyword k)
                      [nsks ks] (split-at (inc ns-parts) parts)
                      ns-key  (ns-key-from-seq nsks)
                      ks (cond-> ns-key
                           ns-key (cons ks))]
                  (assoc-in m ks v)))
              {} m)))

(defn map->datomic
  [m datomic-ns]
  (-> m
      (->> (map-keys (partial set-key-ns datomic-ns)))
      (set/rename-keys {(set-key-ns datomic-ns :id) :db/id})))

(defn datomic->map
  [m]
  (map-keys (partial set-key-ns nil) m))

(defn as-query-string [m]
  (->> m
       (map-keys #(if (keyword? %)
                    (str/replace (name %) #"-" "_")
                    %))
       (map (comp (partial apply str)
                  (partial interpose "=")))
       (interpose "&")
       (cons "?")
       (apply str)))

;; ========== Entity/Attribute Helpers ==========

(extend-protocol EntityReference
  clojure.lang.PersistentArrayMap
  (to-ref-id [em] (:db/id em))
  (to-entity-map [em _] em)
  clojure.lang.PersistentHashMap
  (to-ref-id [em] (:db/id em))
  (to-entity-map [em _] em))

(s/defn pull-ref
  ([connection ref] (pull-ref connection '[*] ref))
  ([connection :- (s/protocol DatabaseReference)
    pattern
    ref :- (s/protocol EntityReference)]
   (->> ref
        (to-ref-id)
        (d/pull (as-db connection) pattern))))

(s/defn entity-exists?
  "returns the entity id if it exists"
  [connection :- (s/protocol DatabaseReference)
   id :- s/Num]
  (ffirst (d/q '[:find ?id
                 :in $ ?id
                 :where
                 [?id]]
               (as-db connection) id)))

(s/defn find-entity
  "returns the entity if it exists"
  ([connection :- (s/protocol DatabaseReference)
    id :- s/Any]
   (when (entity-exists? connection id)
     (to-entity-map id connection)))
  ([connection :- (s/protocol DatabaseReference)
    id :- s/Any
    id-attr :- s/Keyword]
   (let [db (as-db connection)]
     (some->> id
              (d/q '[:find ?e
                     :in $ ?id-attr ?id
                     :where
                     [?e ?id-attr ?id]]
                   db id-attr)
              ffirst
              (d/entity db)))))

(s/defn last-modified
  [connection :- (s/protocol DatabaseReference)
   ref :- (s/protocol EntityReference)]
  (->> ref
       to-ref-id
       (d/q '[:find (max ?when)
              :in $ ?e
              :where
              [?tx :db/txInstant ?when]
              [?e _ _ ?tx]]
            (as-db connection))
       ffirst))

(s/defn update-entity!
  "updates an entity and returns it"
  [connection :- (s/both (s/protocol DatabaseReference)
                         (s/protocol DatomicConnection))
   id :- EntityLookup
   attrs :- {s/Keyword s/Any}]
  @(d/transact (as-conn connection) [(assoc attrs :db/id id)])
  (d/entity (as-db connection) id))

(s/defn retract-attrs!
  [connection :- (s/both (s/protocol DatabaseReference)
                         (s/protocol DatomicConnection))
   id :- EntityLookup
   attrs :- {s/Keyword s/Any}]
  @(d/transact (as-conn connection) (->> (for [[attr v] attrs]
                                           (if-not (coll? v)
                                             [[:db/retract (to-ref-id id) attr v]]
                                             (for [v v]
                                               [:db/retract (to-ref-id id) attr v])))
                                         (mapcat identity)))
  (d/entity (as-db connection) id))

(s/defn delete-entity!
  [connection :- (s/protocol DatomicConnection)
   id :- EntityLookup]
  @(d/transact (as-conn connection) [[:db.fn/retractEntity id]])
  nil)

(s/defn create-entities!
  [connection :- (s/protocol DatomicConnection)
   entities :- [{s/Keyword s/Any}]]
  (let [temps (->> entities
                   (mapv #(assoc % :db/id (d/tempid :db.part/user))))
        {:keys [db-after tempids]} @(d/transact (as-conn connection) temps)]
    (->> temps
         (map (comp #(to-entity-map % connection)
                    (partial d/resolve-tempid db-after tempids)
                    :db/id)))))

(s/defn create-entity!
  [connection :- (s/protocol DatomicConnection)
   entity :- {s/Keyword s/Any}]
  (first (create-entities! connection [entity])))

(s/defn attribute-exists?
  [connection :- (s/protocol DatabaseReference)
   attribute :- s/Keyword]
  (some->> attribute
           (d/q '[:find ?c
                  :in $ ?attr
                  :where [?c :db/ident ?attr]]
                (as-db connection))
           ffirst))

(s/defn confirm-attributes!
  "transacts attributes that do not exist"
  [connection :- (s/protocol DatomicConnection)
   attributes :- [s/Keyword]
   schema-fn :- (s/make-fn-schema {s/Keyword s/Any} [[s/Keyword]])]
  (some->> (for [attr attributes
                 :when (not (attribute-exists? connection attr))]
             (assoc (schema-fn attr)
                    :db/id (d/tempid :db.part/db)))
           seq
           (d/transact (as-conn connection))
           deref))

(defprotocol IValueType
  (datomic-value-type [this]))

(extend-protocol IValueType
  clojure.lang.Keyword
  (datomic-value-type [this] :db.type/keyword)
  java.lang.String
  (datomic-value-type [this] :db.type/string)
  java.lang.Boolean
  (datomic-value-type [this] :db.type/boolean)
  java.lang.Long
  (datomic-value-type [this] :db.type/long)
  java.lang.Float
  (datomic-value-type [this] :db.type/float)
  java.math.BigInteger
  (datomic-value-type [this] :db.type/bigint)
  java.math.BigDecimal
  (datomic-value-type [this] :db.type/double)
  java.util.Date
  (datomic-value-type [this] :db.type/instant)
  java.util.UUID
  (datomic-value-type [this] :db.type/uuid)
  java.net.URI
  (datomic-value-type [this] :db.type/uri))

(extend-protocol IValueType
  (Class/forName "[B")
  (datomic-value-type [this] :db.type/bytes))
