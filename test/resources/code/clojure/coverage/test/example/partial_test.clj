(ns example.partial-test
  (:require
   [example.partial :as partial]
   [clojure.test :refer [deftest is]]))

(deftest covered
  (is (= 2 (partial/covered 1))))

(deftest part
  (is (= 2 (partial/part 1))))

(deftest yellow
  (is (= 2 (partial/yellow 1))))
