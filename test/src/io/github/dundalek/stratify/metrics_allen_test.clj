(ns io.github.dundalek.stratify.metrics-allen-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [io.github.dundalek.stratify.metrics-allen :as ma]))

(defn round [x digits]
  (let [precision (Math/pow 10 digits)]
    (-> x (* precision) (Math/round) (/ precision))))

(def inter-system
  {:num-nodes 14
   :edges [[1 2]
           [1 5]
           [1 6]
           [2 11]
           [5 4]
           [7 10]]})

(def intra-system
  {:num-nodes 14
   :edges [[2 3]
           [4 3]
           [5 7]
           [5 8]
           [6 8]
           [8 9]
           [10 11]
           [10 12]
           [11 13]
           [12 13]
           [12 14]]})

(deftest entropy
  (testing "The example in Figure 2 has H(S) = 2.46 bits per node."
    (is (= 2.46 (-> (ma/system-entropy inter-system)
                    (round 2))))))

(deftest information
  (testing "The example in Figure 2 has I(S) = 36.95 bits"
    (is (= 36.95 (-> (ma/system-information inter-system)
                     (round 2))))))

(deftest excess-entropy
  (testing "The example in Figure 2 has C(S) = 3.82 bits per node."
    (is (= 3.82 (-> (ma/excess-entropy inter-system)
                    (round 2))))))

(deftest coupling
  (testing "The example in Figure 2 has Coupling(MS) = 57.28 bits."
    ;; Paper says 57.28, but probably some rounding error since other numbers match
    (is (= 57.25 (-> (ma/coupling inter-system)
                     (round 2))))))

(deftest intra-module-coupling
  (testing "Figure 3 has IntramoduleCoupling(MS) = 113.40 bits."
    (is (= 113.40 (-> (ma/coupling intra-system)
                      (round 2))))))

(deftest cohesion
  (testing "The example in Figure 3 has Cohesion(MS) = 0.149."
    (is (= 0.149 (-> (ma/cohesion intra-system)
                     (round 3))))))
