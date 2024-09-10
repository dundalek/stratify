(ns io.github.dundalek.stratify.overarch-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.test-utils :refer [is-same?]]
   [stratify.main :as main]))

(deftest extract-default-grouped-namespaces
  (let [prefix "test/resources/overarch/model"
        input-file (str prefix ".edn")
        output-file (str prefix ".dgml")]
    (is (= (slurp output-file)
           (with-out-str
             (main/-main "-f" "overarch" input-file))))
    (main/-main "-f" "overarch" "-o" output-file input-file)
    (is-same? output-file)))
