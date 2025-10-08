(ns io.github.dundalek.stratify.dgml-test
  (:require
   [clojure.data.xml :as xml]
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.dgml :as dgml]
   [io.github.dundalek.stratify.internal :as internal]
   [io.github.dundalek.stratify.kondo :as kondo]
   [loom.graph :as lg]))

(deftest load-graph
  (is (= (lg/digraph (kondo/->graph (kondo/analysis ["test/resources/code/clojure/nested/src"])))
         (dgml/load-graph "test/resources/code/clojure/nested/output-default.dgml")))

  (let [analysis (kondo/analysis ["src"])
        expected (lg/digraph (kondo/->graph analysis))
        actual (dgml/->graph (xml/parse-str (xml/emit-str (internal/analysis->dgml {:analysis analysis}))))]
    ;; Current limitation:
    ;; Comparing just edges, because dgml parsing is missing unconnected nodes.
    ;; Naively adding all nodes would not work, because DGML output contains nested "synthetic" namespaces.
    #_(is (= expected actual))
    (is (= (lg/edges expected) (lg/edges actual)))))
