(ns io.github.dundalek.stratify.graphviz-test
  (:require
   [clojure.test :refer [deftest]]
   [io.github.dundalek.stratify.graphviz :as graphviz]
   [io.github.dundalek.stratify.test-utils :refer [is-same?]]))

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
