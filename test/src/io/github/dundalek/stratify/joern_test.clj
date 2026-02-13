(ns io.github.dundalek.stratify.joern-test
  (:require
   [babashka.json :as json]
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.joern :as joern]
   [io.github.dundalek.stratify.test-utils :as tu]
   [malli.core :as m]
   [malli.transform :as mt]))

(deftest malli-schema-validation
  (let [input-file "test/resources/joern-cpg/out-go/export.json"
        data (json/read-str (slurp input-file) {:key-fn identity})]
    (is (m/validate joern/joern-cpg-graphson-schema data))
    (is (= data (m/coerce joern/joern-cpg-graphson-schema data
                          (mt/strip-extra-keys-transformer))))))

(deftest graphson-extraction-go
  (let [input-file "test/resources/joern-cpg/out-go/export.json"
        output-file "test/resources/joern-cpg/out-go/graph.dgml"]
    (joern/extract {:input-file input-file :output-file output-file})
    (tu/is-same? output-file)))

