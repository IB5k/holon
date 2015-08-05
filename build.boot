(task-options!
 pom {:license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}
      :url "https://github.com/ib5k/holon"
      :scm {:url "https://github.com/ib5k/holon"}})

(def modules
  {:component
   {:project 'ib5k.holon/component
    :version "0.1.0-SNAPSHOT"
    :description "utils for component systems"
    :root "modules/component"
    :dependencies [:clojure
                   :clojurescript
                   :component
                   :schema
                   :aop]
    :test-namespaces '[holon.maker-test
                       holon.component-test]}
   :datomic
   {:project 'ib5k.holon/datomic
    :version "0.1.0-SNAPSHOT"
    :description "datomic components"
    :root "modules/datomic"
    :dependencies [:async
                   :clojure
                   :clojurescript
                   :component
                   :datomic
                   [:logging :clj]
                   :manifold
                   :schema]
    :test-namespaces '[holon.events-test]}
   :test
   {:project 'ib5k.holon/test
    :version "0.1.0-SNAPSHOT"
    :description "test utils for component systems"
    :root "modules/test"
    :dependencies [:clojure
                   :clojurescript
                   :component
                   [:holon :component]]}})

(def dependencies
  (merge
   {:holon          (->> (for [[k {:keys [project version]}] modules]
                           [k [[project version]]])
                         (into {}))}
   '{:aop           [[tangrammer/co-dependency "0.1.5"]
                     [milesian/aop "0.1.5"]
                     [milesian/identity "0.1.4"]]
     :async         [[org.clojure/core.async "0.1.346.0-17112a-alpha"]]
     :boot          [[adzerk/bootlaces "0.1.11"]
                     [adzerk/boot-cljs "0.0-2814-4"]
                     [adzerk/boot-cljs-repl "0.1.10-SNAPSHOT"]
                     [adzerk/boot-test "1.0.4"]
                     [boot-cljs-test/node-runner "0.1.0"]
                     [boot-garden "1.2.5-2"]
                     [ib5k/boot-component "0.1.2-SNAPSHOT"]
                     [jeluard/boot-notify "0.1.2"]]
     :clojure       [[org.clojure/clojure "1.8.0-alpha4"]]
     :clojurescript [[org.clojure/clojurescript "0.0-3308"]]
     :component
     {:clj          [[com.stuartsierra/component "0.2.3"]]
      :cljs         [[quile/component-cljs "0.2.4"]]}
     :datascript    [[datascript "0.10.0"]]
     :datomic       [[com.datomic/datomic-pro "0.9.5153"]
                     [juxt.modular/datomic "0.2.1"
                      :exclusions [com.datomic/datomic-free]]
                     [io.rkn/conformity "0.3.4"
                      :exclusions [com.datomic/datomic-free]]
                     [datomic-schema "1.3.0"]]
     :email         [[com.draines/postal "1.11.3"]]
     :filesystem
     {:io           [[me.raynes/fs "1.4.6"]]}
     :logging
     {:clj          [[com.taoensso/timbre "4.0.2"]
                     [ch.qos.logback/logback-classic "1.1.3"]]
      :cljs         [[shodan "0.4.1"]]}
     :manifold      [[manifold "0.1.0"]]
     :repl          [[com.cemerick/piggieback "0.2.1"]
                     [org.clojure/tools.namespace "0.2.11"]
                     [org.clojure/tools.nrepl "0.2.10"]
                     [weasel "0.7.0"]
                     [cider/cider-nrepl "0.9.0-SNAPSHOT"]]
     :schema        [[prismatic/plumbing "0.4.2"]
                     [prismatic/schema "0.4.0"]
                     [ib5k/component-schema "0.1.2-SNAPSHOT"]]
     :reader        [[org.clojure/tools.reader "0.9.1"]]
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
                 (->> (build-deps dependencies :boot :test [:holon :test] :component)
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
 '[jeluard.boot-notify        :refer :all])

(task-options!
 cljs {:compiler-options {:warnings {:single-segment-namespace false}}})

(deftask module
  "set environment for a module"
  [m id KEYWORD kw "The id of the component"]
  (let [{:keys [version root test-namespaces] :as module} (get modules id)]
    (task-options!
     pom #(merge % (select-keys module [:project :version :description]))
     cljs-test-node-runner {:namespaces test-namespaces})
    (bootlaces! version)
    (-> module
        (assoc :source-paths #(conj % (str root "/src")))
        (assoc :resource-paths #(conj % (str root "/src")))
        (update :dependencies (fn [deps]
                                #(->> deps
                                      (apply (partial build-deps dependencies))
                                      (concat %)
                                      (vec))))
        (->> (mapcat identity)
             (apply set-env!)))
    identity))

(deftask test-all
  "test clj and cljs"
  []
  (let [root (get-env :root)]
    (set-env! :source-paths #(conj % (str root "/test")))
    (set-env! :resource-paths #(conj % (str root "/test")))
    (comp
     (test)
     (cljs-test-node-runner)
     (cljs :source-map true
           :pretty-print true)
     (run-cljs-test))))

(deftask dev
  "watch and compile cljs, init cljs-repl"
  []
  (let [root (get-env :root)]
    (set-env! :source-paths #(conj % (str root "/test")))
    (set-env! :resource-paths #(conj % (str root "/test")))
    (comp
     (watch)
     (notify)
     (cljs-repl :port 3458)
     (reload-system)
     (cljs :source-map true
           :pretty-print true))))
