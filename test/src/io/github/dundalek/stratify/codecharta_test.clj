(ns io.github.dundalek.stratify.codecharta-test
  (:require
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [io.github.dundalek.stratify.codecharta :as cc]
   [io.github.dundalek.stratify.kondo :as kondo]
   [io.github.dundalek.stratify.test-utils :refer [is-same?]]
   [snap.core :as snap]))

(deftest build-tree
  (is (=
       {:name "root"
        :type "Folder"
        :attributes {}
        :children #{{:name "src"
                     :type "Folder"
                     :attributes {}
                     :children #{{:name "c.clj"
                                  :type "File"
                                  :attributes {:id :c}}
                                 {:name "example"
                                  :type "Folder"
                                  :attributes {}
                                  :children #{{:name "a.clj"
                                               :type "File"
                                               :attributes {:id :a}}
                                              {:name "b.clj"
                                               :type "File"
                                               :attributes {:id :b}}}}}}}}
       (walk/postwalk
        (fn [x]
          (if (and (map? x) (:children x))
            (update x :children set)
            x))
        (cc/build-tree
         {"src/example/a.clj" {:id :a}
          "src/example/b.clj" {:id :b}
          "src/c.clj" {:id :c}})))))

(deftest codecharta
  (is (snap/match-snapshot
       ::codecharta
       (cc/->codecharta {:analysis (kondo/analysis ["test/resources/nested/src"])}))))

(deftest with-coverage
  (let [output-file "test/resources/coverage/coverage.cc.json"]
    (cc/extract-clj {:repo-path "test/resources/coverage"
                     :source-paths ["src"]
                     :coverage-file "test/resources/coverage/target/coverage/codecov.json"
                     :output-file output-file})
    (is-same? output-file)))
