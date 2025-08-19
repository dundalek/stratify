(ns io.github.dundalek.stratify.joern-test
  (:require
   [babashka.fs :as fs]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [io.github.dundalek.stratify.joern :as joern]
   [io.github.dundalek.stratify.joern.graphson :as graphson]
   [io.github.dundalek.stratify.test-utils :as tu]))

(defn- canonicalize-xml
  "Simple XML canonicalization that sorts content and removes blank strings"
  [element]
  (walk/postwalk
   (fn [item]
     (cond-> item
       (and (map? item) (contains? item :content))
       (update :content
               (fn [content]
                 (->> content
                      (remove #(and (string? %) (str/blank? %)))
                      (sort-by str))))))
   element))

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

(deftest graphml-graphson-equivalence
  (let [graphml-output "test/output/joern-go.dgml"
        graphson-output (fs/file (fs/create-temp-file {:prefix "joern-go-graphson" :suffix ".dgml"}))]
    (try
      (graphson/extract {:input-file "test/resources/joern-cpg/out-go/export.json"
                         :output-file (str graphson-output)})
      (is (= (-> graphml-output io/reader xml/parse canonicalize-xml)
             (-> graphson-output io/reader xml/parse canonicalize-xml)))
      (finally
        (fs/delete-if-exists graphson-output)))))
