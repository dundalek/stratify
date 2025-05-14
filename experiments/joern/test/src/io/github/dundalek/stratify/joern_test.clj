(ns io.github.dundalek.stratify.joern-test
  (:require
   [clojure.test :refer [deftest]]
   [io.github.dundalek.stratify.joern :as joern]
   [io.github.dundalek.stratify.test-utils :as tu]))

(deftest sample-go
  (let [output-file "test/output/joern-go.dgml"]
    (joern/extract {:input-file "test/resources/joern-cpg/out-go/export.xml"
                    :output-file output-file})
    (tu/is-same? output-file)))

#_(deftest sample-py
    (let [output-file "test/output/joern-py.dgml"]
      (joern/extract {:input-file "test/resources/joern-cpg/out-py/export.xml"
                      :output-file output-file})
      (tu/is-same? output-file)))

#_(deftest sample-rb
    (let [output-file "test/output/joern-rb.dgml"]
      (joern/extract {:input-file "test/resources/joern-cpg/out-rb/export.xml"
                      :output-file output-file})
      (tu/is-same? output-file)))

(deftest sample-ts
  (let [output-file "test/output/joern-ts.dgml"]
    (joern/extract {:input-file "test/resources/joern-cpg/out-ts/export.xml"
                    :output-file output-file})
    (tu/is-same? output-file)))
