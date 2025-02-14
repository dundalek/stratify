(ns io.github.dundalek.stratify.dgml-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.dgml :as dgml]
   [io.github.dundalek.stratify.kondo :as kondo]
   [loom.graph :as lg]))

(deftest load-graph
  (is (= (lg/digraph (kondo/->graph (kondo/analysis ["test/resources/nested/src"])))
         (dgml/load-graph  "test/resources/nested/output-default.dgml"))))
