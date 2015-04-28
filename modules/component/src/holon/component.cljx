(ns holon.component
  (:require [#+clj  com.stuartsierra.component
             #+cljs quile.component
             :as component :refer [system-map #+cljs SystemMap]]
            #+clj [tangrammer.component.co-dependency :as co-dependency]
            #+clj  [schema.core :as s]
            #+cljs [schema.core :as s :include-macros true]
            #+clj [milesian.identity :as identity]
            [ib5k.component.ctr :as ctr]
            [ib5k.component.using-schema :as us]
            [plumbing.core :refer [map-vals]])
  #+clj
  (:import [com.stuartsierra.component SystemMap]))

(s/defschema ComponentMap
  {s/Keyword {:cmp s/Any
              (s/optional-key :using) us/Dependencies
              #+clj (s/optional-key :co-using) #+clj us/Dependencies}})

(s/defschema UpdateComponent
  [(s/one (s/make-fn-schema [[s/Any]] [[s/Any]]) "cmp update fn") s/Any])

#+clj
(s/defn system-co-using-schema :- us/SystemMap
  "same as component/system using but allows prismatic schema to specify components
  ex. {:webrouter [:public-resources (s/protocol RouteProvider)]}
  components are automatically prevented from depending on themselves"
  [system :- us/SystemMap
   system-dependencies :- {s/Keyword us/Dependencies}]
  (->> system-dependencies
       (map-vals (partial us/expand-dependency-map-schema system))
       (us/remove-self-dependencies)
       (co-dependency/system-co-using system)))

(s/defn extract-key :- {s/Keyword s/Any}
  [component-map :- ComponentMap
   key :- (s/enum :cmp :using #+clj :co-using)]
  (->> component-map
       (map-vals key)
       (remove (comp nil? second))
       (into {})))

(s/defn new-system :- us/SystemMap
  [component-map :- ComponentMap]
  (let [system (->> (extract-key component-map :cmp)
                    (apply concat)
                    (apply system-map))]
    (-> system
        (us/system-using-schema (extract-key component-map :using))
        #+clj (system-co-using-schema (extract-key component-map :co-using)))))

;; taken from https://github.com/milesian/BigBang/blob/master/src/milesian/bigbang.clj
(s/defn expand :- us/SystemMap
  [system-map :- us/SystemMap
   {:keys [before-start
           after-start]
    :or {before-start []
         after-start []}} :- {(s/optional-key :before-start) [UpdateComponent]
                              (s/optional-key :after-start) [UpdateComponent]}]
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
(s/defn start :- us/SystemMap
  [system :- us/SystemMap]
  (let [system-atom (atom system)]
    (expand system {:before-start [[identity/add-meta-key system]
                                   [co-dependency/assoc-co-dependencies system-atom]
                                   [ctr/validate-class]]
                    :after-start [[co-dependency/update-atom-system system-atom]
                                  [ctr/validate-class]]})))

#+cljs
(s/defn start :- us/SystemMap
  [system :- us/SystemMap]
  (expand system {:before-start [[ctr/validate-class]]
                  :after-start [[ctr/validate-class]]}))
