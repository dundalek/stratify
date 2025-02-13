(ns io.github.dundalek.stratify.metrics-lcom-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.kondo :as kondo]
   [io.github.dundalek.stratify.metrics-lcom :as metrics-lcom]))

(deftest namespaces-connected-components-count
  (is (= '{example1 2 example2 1}
         (metrics-lcom/namespaces-connected-components-count
          (:analysis (kondo/run-kondo ["test/resources/connected-components/src"]))))))
