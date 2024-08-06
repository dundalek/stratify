(ns io.github.dundalek.stratify.metrics-lakos-test
  (:require
   [clojure.test :refer [are deftest is]]
   [io.github.dundalek.stratify.metrics-lakos :as metrics]
   [loom.graph :as lg]))

;; https://www.archunit.org/userguide/html/000_Index.html#_cumulative_dependency_metrics_by_john_lakos
(def g (lg/digraph
        {"one" #{"two"}
         "two" #{"four" "five" "six"}
         "three" #{"two"}
         "four" #{}
         "five" #{"six"}}))

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

; (deftest normalized-cumulative-component-dependency
;   (is (= 1.29 (metrics/normalized-cumulative-component-dependency g))))

(let [g (lg/digraph
         ["A" "B"]
         ["B" "C"]
         ["C" "D"]
         ["D" "E"]
         ["E" "F"])]
  (is (= [21 (/ 21 6.0) (/ 21 6.0 6.0) (/ 21 14.0)]
         [(metrics/cumulative-component-dependency g)
          (metrics/average-component-dependency g)
          (metrics/relative-average-component-dependency g)
          (metrics/normalized-cumulative-component-dependency g)])))
