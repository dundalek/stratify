(ns io.github.dundalek.stratify.metrics-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.kondo :as kondo]
   [io.github.dundalek.stratify.metrics :as metrics]
   [loom.graph :as lg]
   [snap.core :as snap]))

(deftest metrics
  (let [analysis (kondo/analysis ["test/resources/code/clojure/layered-valid/src"])
        g (lg/digraph (kondo/->graph analysis))]
    (is (snap/match-snapshot
         ::metrics
         {:element-metrics (metrics/element-metrics g {:metrics (disj metrics/all-metrics :eigenvector-centrality)})
          :system-metrics (metrics/system-metrics g)}))))
          ; :analysis-element-metrics (metrics/analysis-element-metrics analysis)
          ; :analysis-system-metrics (metrics/analysis-system-metrics analysis)}))))
