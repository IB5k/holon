(ns holon.component
  (:require [#+clj  com.stuartsierra.component
             #+cljs quile.component
             :as component :refer [system-map]]
            #+clj [tangrammer.component.co-dependency :as co-dependency]
            #+clj  [schema.core :as s]
            #+cljs [schema.core :as s :include-macros true]
            #+clj [milesian.identity :as identity]
            [ib5k.component.ctr :as ctr]
            [ib5k.component.using-schema :refer [expand-dependency-map-schema system-using-schema remove-self-dependencies]]
            [plumbing.core :refer [map-vals]]))

;; taken from https://github.com/milesian/BigBang/blob/master/src/milesian/bigbang.clj
(defn expand
  [system-map {:keys [before-start after-start]}]
  (let [on-start-sequence (apply conj before-start (cons [component/start] after-start))
        start (fn [c & args]
                (apply (->> on-start-sequence
                            (mapv (fn [[f & args]]
                                    #(apply f (conj args %))))
                            reverse
                            (apply comp))
                       (conj args c)))]
    (component/update-system system-map (keys system-map) start)))

#+clj
(defn system-co-using-schema
  "same as component/system using but allows prismatic schema to specify components
  ex. {:webrouter [:public-resources (s/protocol RouteProvider)]}
  components are automatically prevented from depending on themselves"
  [system dependency-map]
  (->> dependency-map
       (map-vals (partial expand-dependency-map-schema system))
       (remove-self-dependencies)
       (co-dependency/system-co-using system)))

(defn extract-key [component-map key]
  (->> component-map
       (map-vals key)
       (remove (comp nil? second))
       (into {})))

(s/defn new-system
  [component-map :- {s/Keyword {:cmp s/Any
                                :using (s/either [s/Any]
                                                 {s/Any s/Any})}}]
  (let [system (->> (extract-key component-map :cmp)
                    (apply concat)
                    (apply system-map))]
    (-> system
        (system-using-schema (extract-key component-map :using))
        #+clj
        (system-co-using-schema (extract-key component-map :co-using)))))

#+clj
(defn start [system]
  (let [system-atom (atom system)]
    (expand system {:before-start [[identity/add-meta-key system]
                                   [co-dependency/assoc-co-dependencies system-atom]
                                   [ctr/validate-class]]
                    :after-start [[co-dependency/update-atom-system system-atom]
                                  [ctr/validate-class]]})))

#+cljs
(defn start [system]
  (expand system {:before-start [[ctr/validate-class]]
                  :after-start [[ctr/validate-class]]}))
