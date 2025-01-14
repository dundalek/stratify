(ns io.github.dundalek.stratify.metrics-lakos-test
  (:require
   [clojure.test :refer [are deftest is]]
   [io.github.dundalek.stratify.metrics-lakos :as metrics]
   [loom.graph :as lg]))

(defn round-two-decimal-places [x]
  (-> x (* 100.0) (Math/round) (/ 100.0)))

;; https://www.archunit.org/userguide/html/000_Index.html#_cumulative_dependency_metrics_by_john_lakos
(def g (lg/digraph
        {"one" #{"two"}
         "two" #{"four" "five" "six"}
         "three" #{"two"}
         "four" #{}
         "five" #{"six"}}))

(defn metrics-map [adj]
  (let [g (lg/digraph adj)]
    {:ccd (metrics/cumulative-component-dependency g)
     :acd (metrics/average-component-dependency g)
     :racd (metrics/relative-average-component-dependency g)
     :nccd (metrics/normalized-cumulative-component-dependency g)}))

(deftest depends-on-count
  (are [node expected] (= expected (metrics/count-transitive-dependencies g node))
    "one" 5
    "two" 4
    "three" 5
    "four" 1
    "five" 2
    "six" 1))

(deftest cumulative-component-dependency
  (is (= 18 (metrics/cumulative-component-dependency g))))

(deftest average-component-dependency
  (is (= 3.0 (metrics/average-component-dependency g))))

(deftest relative-average-component-dependency
  (is (= 0.5 (metrics/relative-average-component-dependency g))))

(deftest normalized-cumulative-component-dependency
  (is (= 1.29 (-> (metrics/normalized-cumulative-component-dependency g)
                  (round-two-decimal-places)))))

(deftest simple-graph
  (is (= {:ccd 3 :acd 1.5 :racd 0.75 :nccd 1.0}
         (metrics-map {:a #{:b}}))))

(deftest other-graph
  (is (= {:ccd 21 :acd (/ 21 6.0) :racd (/ 21 6.0 6.0) :nccd (/ 21 14.0)}
         (metrics-map {"A" #{"B"}
                       "B" #{"C"}
                       "C" #{"D"}
                       "D" #{"E"}
                       "E" #{"F"}}))))
