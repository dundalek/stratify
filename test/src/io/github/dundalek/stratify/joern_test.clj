(ns ;^:focus
 io.github.dundalek.stratify.joern-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.joern :as joern]
   [babashka.json :as json]
   [malli.core :as m]
   [malli.transform :as mt]))

(deftest malli-schema-validation
  (let [input-file "experiments/joern/test/resources/joern-cpg/out-go/export.json"
        data (json/read-str (slurp input-file) {:key-fn identity})]
    (is (m/validate joern/joern-cpg-graphson-schema data))
    (is (= data (m/coerce joern/joern-cpg-graphson-schema data
                          (mt/strip-extra-keys-transformer))))))
