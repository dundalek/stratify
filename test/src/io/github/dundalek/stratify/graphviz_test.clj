(ns io.github.dundalek.stratify.graphviz-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.graphviz :as graphviz]
   [io.github.dundalek.stratify.test-utils :refer [is-same?]]
   [stratify.main :as main]))

(deftest simple-via-cli
  (let [file-prefix "test/resources/graphviz/simple"
        output-file (str file-prefix ".dgml")]
    (is (= (slurp output-file)
           (with-out-str
             (main/-main "-f" "dot" (str file-prefix ".dot")))))))

(deftest simple
  (let [file-prefix "test/resources/graphviz/simple"
        output-file (str file-prefix ".dgml")]
    (graphviz/extract {:input-file (str file-prefix ".dot")
                       :output-file output-file})
    (is-same? output-file)))

(deftest nested
  (let [file-prefix "test/resources/graphviz/project"
        output-file (str file-prefix ".dgml")]
    (graphviz/extract {:input-file (str file-prefix ".dot")
                       :output-file output-file})
    (is-same? output-file)))

(deftest nested-flat
  (let [file-prefix "test/resources/graphviz/project"
        output-file (str file-prefix "-flat.dgml")]
    (graphviz/extract {:input-file (str file-prefix ".dot")
                       :output-file output-file
                       :flat-namespaces true})
    (is-same? output-file)))

(deftest clusters
  ;; asserting current behavior but the result is wrong because graphviz cluster support is not yet implemented
  (let [file-prefix "test/resources/graphviz/clusters"
        output-file (str file-prefix ".dgml")]
    (graphviz/extract {:input-file (str file-prefix ".dot")
                       :output-file output-file})
    (is-same? output-file)))
