(ns ;^:focus
 io.github.dundalek.stratify.joern-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.joern :as joern]
   [jsonista.core :as j]
   [malli.core :as m]
   [malli.transform :as mt]))

(deftest malli-schema-validation
  (let [input-file "experiments/joern/test/resources/joern-cpg/out-go/export.json"
        data (j/read-value (slurp input-file) j/default-object-mapper)]
    (is (m/validate joern/joern-cpg-graphson-schema data))
    (is (= data (m/coerce joern/joern-cpg-graphson-schema data
                          (mt/strip-extra-keys-transformer))))))
