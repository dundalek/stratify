(ns io.github.dundalek.stratify.joern-test
  (:require
   [babashka.json :as json]
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.joern :as joern]
   [io.github.dundalek.stratify.test-utils :as tu]
   [malli.core :as m]
   [malli.transform :as mt]
   [stratify.main-jvm :as main]))

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

(deftest extract-go-test
  (let [root-path "test/resources/code/go/greeting"
        output-file "test/resources/joern-cpg/out-go/extracted.dgml"]
    (main/main* "-f" "go-joern" "-o" output-file root-path)
    (tu/is-same? output-file)))

(deftest extract-c-test
  (let [root-path "test/resources/code/c/greeting"
        output-file "test/resources/joern-cpg/out-c/extracted.dgml"]
    (main/main* "-f" "c-joern" "-o" output-file root-path)
    (tu/is-same? output-file)))

(deftest extract-java-test
  (let [root-path "test/resources/code/java/greeting/src"
        output-file "test/resources/joern-cpg/out-java/extracted.dgml"]
    (main/main* "-f" "java-joern" "-o" output-file root-path)
    (tu/is-same? output-file)))

(deftest extract-javascript-test
  (let [root-path "test/resources/code/javascript/greeting"
        output-file "test/resources/joern-cpg/out-javascript/extracted.dgml"]
    (main/main* "-f" "js-joern" "-o" output-file root-path)
    (tu/is-same? output-file)))

(deftest extract-python-test
  (let [root-path "test/resources/code/python/greeting"
        output-file "test/resources/joern-cpg/out-python/extracted.dgml"]
    (main/main* "-f" "python-joern" "-o" output-file root-path)
    (tu/is-same? output-file)))

