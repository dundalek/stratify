(ns io.github.dundalek.stratify.internal-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.internal :as stratify]
   [io.github.dundalek.stratify.test-utils :as tu :refer [is-same?]]
   [stratify.main :as main]))

(deftest extract-default-grouped-namespaces-via-cli
  (let [output-file "test/resources/sample/output.dgml"]
    (is (= (slurp output-file)
           (with-out-str
             (main/-main "test/resources/sample/src"))))))

(deftest extract-default-grouped-namespaces
  (let [output-file "test/resources/sample/output.dgml"]
    (stratify/extract {:source-paths ["test/resources/sample/src"]
                       :output-file output-file})
    (is-same? output-file)))

(deftest extract-flat-namespaces
  (let [output-file "test/resources/sample/output-flat.dgml"]
    (stratify/extract {:source-paths ["test/resources/sample/src"]
                       :output-file output-file
                       :flat-namespaces true})
    (is-same? output-file)))

(deftest extract-include-dependencies
  (let [output-file "test/resources/sample/output-deps.dgml"]
    (stratify/extract {:source-paths ["test/resources/sample/src"]
                       :output-file output-file
                       :include-dependencies true})
    (is-same? output-file)))

(deftest extract-include-dependencies-flat
  (let [output-file "test/resources/sample/output-deps-flat.dgml"]
    (stratify/extract {:source-paths ["test/resources/sample/src"]
                       :output-file output-file
                       :include-dependencies true
                       :flat-namespaces true})
    (is-same? output-file)))

(deftest extract-nested-default
  (let [output-file "test/resources/nested/output-default.dgml"]
    (stratify/extract {:source-paths ["test/resources/nested/src"]
                       :output-file output-file})
    (is-same? output-file)))

(deftest extract-nested-extra-node
  (let [output-file "test/resources/nested/output-extra-node.dgml"]
    (stratify/extract {:source-paths ["test/resources/nested/src"]
                       :output-file output-file
                       :insert-namespace-node "SELF"})
    (is-same? output-file)))

(deftest extract-with-coverage
  (let [output-file "test/resources/coverage/output-coverage.dgml"]
    (stratify/extract {:source-paths ["test/resources/coverage/src"]
                       :output-file output-file
                       :coverage-file "test/resources/coverage/target/coverage/codecov.json"})
    (is-same? output-file)))
