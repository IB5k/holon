(task-options!
 pom {:description "components"
      :license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}
      :url "https://github.com/ib5k/holon"
      :scm {:url "https://github.com/ib5k/holon"}})

(def modules
  {:events
   {:project 'ib5k.holon/events
    :version "0.1.0-SNAPSHOT"
    :description "event dispatcher"
    :root "modules/events"
    :dependencies [:clojure
                   :clojurescript
                   :component
                   :schema]}
   :test
   {:project 'ib5k.holon/test
    :version "0.1.0-SNAPSHOT"
    :description "test utils for component systems"
    :root "modules/test"
    :dependencies [:clojure
                   :clojurescript
                   :component]}})

(def dependencies
  (merge
   {:holon          (->> (for [[k {:keys [project version]}] modules]
                           [k [project version]])
                         (into {}))}
   '{:async         [[org.clojure/core.async "0.1.346.0-17112a-alpha"]]
     :boot          [[adzerk/bootlaces "0.1.11"]
                     [adzerk/boot-cljs "0.0-2814-4"]
                     [adzerk/boot-cljs-repl "0.1.10-SNAPSHOT"]
                     [adzerk/boot-test "1.0.4"]
                     [boot-cljs-test/node-runner "0.1.0"]
                     [boot-garden "1.2.5-2"]
                     [deraen/boot-cljx "0.2.2"]
                     [ib5k/boot-component "0.1.2-SNAPSHOT"]
                     [jeluard/boot-notify "0.1.2"]]
     :clojure       [[org.clojure/clojure "1.7.0-beta1"]
                     [org.clojure/core.match "0.3.0-alpha4"]]
     :clojurescript [[org.clojure/clojurescript "0.0-3211"]]
     :component
     {:clj          [[com.stuartsierra/component "0.2.3"]]
      :cljs         [[quile/component-cljs "0.2.4"]]}
     :datascript    [[datascript "0.10.0"]]
     :datomic       [[com.datomic/datomic-pro "0.9.5153"]
                     [juxt.modular/datomic "0.2.1"]
                     [io.rkn/conformity "0.3.4"]]
     :email         [[com.draines/postal "1.11.3"]]
     :filesystem
     {:io           [[me.raynes/fs "1.4.6"]]}
     :garden        [[garden "1.2.5"]
                     [trowel "0.1.0-SNAPSHOT"]]
     :html          [[hiccup "1.0.5"]
                     [reagent "0.5.0"]]
     :http-requests
     {:clj          [[clj-http "1.1.0"]
                     [org.apache.httpcomponents/httpclient "4.4"]
                     [cheshire "5.4.0"]
                     [com.cemerick/url "0.1.1"]]
      :cljs         [[cljs-http "0.1.30"]
                     [camel-snake-kebab "0.3.1"]]}
     :logging
     {:clj          [[com.taoensso/timbre "3.4.0"]]
      :cljs         [[shodan "0.4.1"]]}
     :modular
     {:bidi         [[juxt.modular/bidi "0.9.2"]]
      :http-kit     [[juxt.modular/http-kit "0.5.4"]
                     [http-kit "2.1.19"]]
      :maker        [[juxt.modular/maker "0.5.0"]
                     [juxt.modular/wire-up "0.5.0"]]
      :ring         [[juxt.modular/ring "0.5.2"]]}
     :repl          [[com.cemerick/piggieback "0.2.0"]
                     [org.clojure/tools.namespace "0.2.10"]
                     [org.clojure/tools.nrepl "0.2.10"]
                     [weasel "0.7.0-SNAPSHOT"]
                     [cider/cider-nrepl "0.9.0-SNAPSHOT"]
                     [com.keminglabs/cljx "0.6.0"]]
     :server
     {:ring         [[ring "1.3.2"]
                     [ring/ring-defaults "0.1.4"]
                     [fogus/ring-edn "0.2.0"]]}
     :schema        [[prismatic/plumbing "0.4.2"]
                     [prismatic/schema "0.4.0"]
                     [ib5k/component-schema "0.1.2-SNAPSHOT"]]
     :reader        [[org.clojure/tools.reader "0.9.1"]]
     :sente         [[com.taoensso/encore "1.23.1"]]
     :template      [[juxt.modular/template "0.6.3"]]
     :test
     {:check        [[org.clojure/test.check "0.7.0"]]
      :cljs         [[com.cemerick/clojurescript.test "0.3.3"]]}
     :time
     {:clj          [[clj-time "0.9.0"]]
      :cljs         [[com.andrewmcveigh/cljs-time "0.3.3"]]}
     :viz           [[rhizome "0.2.4"]]}))

(defn make-korks [korks]
  (cond-> korks
    (keyword? korks) vector))

(defn flatten-vals
  "takes a hashmap and recursively returns a flattened list of all the values"
  [coll]
  (if ((every-pred coll? sequential?) coll)
    coll
    (mapcat flatten-vals (vals coll))))

(defn build-deps [deps & korks]
  (->> korks
       (mapv (comp (partial get-in deps) make-korks))
       (mapcat flatten-vals)
       (into [])))

(set-env!
 :source-paths #{}
 :resource-paths #{}
 :dependencies (fn [deps]
                 (->> (build-deps dependencies :boot :test [:holon :test])
                      (mapv #(conj % :scope "test"))
                      (concat deps)
                      vec)))

(require
 '[adzerk.bootlaces           :refer :all]
 '[adzerk.boot-cljs           :refer :all]
 '[adzerk.boot-cljs-repl      :refer :all]
 '[adzerk.boot-test           :refer [test]]
 '[boot-cljs-test.node-runner :refer :all]
 '[boot-garden.core           :refer :all]
 '[boot-component.reloaded    :refer :all]
 '[deraen.boot-cljx           :refer :all]
 '[jeluard.boot-notify        :refer :all])

(deftask module
  "set environment for a module"
  [m id  KEYWORD kw "The id of the component"]
  (let [{:keys [version root] :as module} (get modules id)]
    (task-options!
     pom (select-keys module [:project :version]))
    (bootlaces! version)
    (-> module
        (select-keys [:dependencies :root])
        (assoc :source-paths #(conj % (str root "/src")))
        (update :dependencies (fn [deps]
                                #(->> deps
                                      (apply (partial build-deps dependencies))
                                      (concat %)
                                      (vec))))
        (->> (mapcat identity)
             (apply set-env!)))))

(deftask test-all
  "test clj and cljs"
  []
  (let [root (get-env :root)]
    (set-env! :source-paths #(conj % (str root "/test")))
    (comp
     (cljx)
     (test)
     (cljs-test-node-runner)
     (cljs :source-map true
           :pretty-print true)
     (run-cljs-test))))

(deftask dev
  "watch and compile cljx, cljs, init cljs-repl"
  []
  (let [root (get-env :root)]
    (set-env! :source-paths #(conj % (str root "/test")))
    (set-env! :resource-paths #(conj % (str root "/src") (str root "/test")))
    (comp
     (watch)
     (notify)
     (cljx)
     (cljs-repl :port 3458)
     (reload-system)
     (cljs :source-map true
           :pretty-print true))))
