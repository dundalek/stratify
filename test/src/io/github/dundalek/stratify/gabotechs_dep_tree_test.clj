(ns io.github.dundalek.stratify.gabotechs-dep-tree-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.test-utils :refer [is-same?]]
   [stratify.main :as main]))

(deftest extract
  (let [output-file "test/resources/gabotechs-dep-tree/layered-valid.json"]
    (main/main* "--to" "dep-tree" "--out" output-file "test/resources/code/clojure/layered-valid/src")
    (is-same? output-file)

    (is (= (slurp output-file)
           (with-out-str
             (main/main* "--to" "dep-tree" "test/resources/code/clojure/layered-valid/src"))))))
