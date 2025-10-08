(ns io.github.dundalek.stratify.queries-test
  (:require
   [clojure.test :refer [deftest is]]
   [datascript.core :as d]
   [io.github.dundalek.stratify.queries :as q]))

(def conn-valid (q/load-sources ["test/resources/code/clojure/layered-valid/src"]))
(def conn-invalid (q/load-sources ["test/resources/code/clojure/layered-invalid/src"]))

(deftest namespace-matcher
  (let [matches? (q/namespace-matcher ".*durable.*")]
    (is (true? (matches? 'asami.durable.graph)))
    (is (false? (matches? 'asami.cache)))

    (is (true? (matches? 'asami.durable-foo.graph))))) ; todo

(deftest check-queries
  (is (empty? (q/check-architectural-constraints conn-valid)))
  (is (seq (q/check-architectural-constraints conn-invalid)))

  (is (empty? (q/check-dependencies conn-valid)))
  (is (seq (q/check-dependencies conn-invalid)))

  (is (empty? (q/check-layers conn-valid)))
  (is (seq (q/check-layers conn-invalid))))
