(ns io.github.dundalek.stratify.internal-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.internal :as stratify]
   [io.github.dundalek.stratify.test-utils :refer [is-same?]]))

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

(deftest color-add-alpha
  (is (= "#EF123456" (stratify/color-add-alpha "#123456" "EF"))))
