(ns holon.datomic.utils
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [plumbing.core :refer :all]
            [schema.core :as s]
            [datomic.api :as d]
            [juxt.datomic.extras :refer (DatomicConnection as-conn as-db to-ref-id to-entity-map EntityReference)]))

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
  (to-entity-map [em _] em))

(defn pull-ref [connection ref]
  (->> ref
       (to-ref-id)
       (d/pull (as-db connection) '[*])))

(defn create-entities! [connection entities]
  (let [temps (->> entities
                   (mapv #(assoc % :db/id (d/tempid :db.part/user))))
        {:keys [db-after tempids]} @(d/transact (as-conn connection) temps)]
    (->> temps
         (map (comp #(to-entity-map % connection)
                    (partial d/resolve-tempid db-after tempids)
                    :db/id)))))

(defn create-entity! [connection entity]
  (first (create-entities! connection [entity])))

(defn attribute-exists? [connection attribute]
  (some->> attribute
           (d/q '[:find ?c
                :in $ ?attr
                :where [?c :db/ident ?attr]]
              (as-db connection))
           ffirst))

(defn confirm-attributes!
  "transacts attributes that do not exist"
  [connection attributes schema-fn]
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
