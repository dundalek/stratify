(ns io.github.dundalek.stratify.internal-test
  (:require
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [io.github.dundalek.stratify.internal :as stratify]))

(defn is-same? [path]
  (let [result (sh "git" "status" "--porcelain" path)]
    (testing path
      (is (= 0 (:exit result)))
      (is (= "" (:err result)))
      (is (= [""] (str/split-lines (:out result)))))))

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
