(ns example.covered-test
  (:require
   [example.covered :as covered]
   [clojure.test :refer [deftest is]]))

(deftest covered
  (is (= 2 (covered/covered 1))))
