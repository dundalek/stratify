(ns io.github.dundalek.stratify.codecharta-test
  (:require
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [io.github.dundalek.stratify.codecharta :as cc]
   [io.github.dundalek.stratify.internal :as internal]
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
       (cc/->codecharta {:analysis (:analysis (internal/run-kondo ["test/resources/nested/src"]))}))))
