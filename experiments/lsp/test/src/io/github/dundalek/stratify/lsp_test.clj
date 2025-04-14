(ns io.github.dundalek.stratify.lsp-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [stratify.lsp :as lsp]
   [loom.graph :as lg]))

(defn- relativize-graph [root-path g]
  (let [uri-base (str "file://" root-path "/")
        transform-id #(some-> % (str/replace-first uri-base ""))
        g (-> (lg/digraph)
              (lg/add-nodes* (->> (lg/nodes g) (map transform-id)))
              (lg/add-edges* (->> (lg/edges g) (map (fn [[source target]]
                                                      [(transform-id source) (transform-id target)]))))
              (assoc :attrs (reduce-kv
                             (fn [m k v]
                               (assoc m
                                      (transform-id k)
                                      (update v :parent transform-id)))
                             {}
                             (:attrs g))))]

    g))

(defn- make-digraph [{:keys [adj attrs]}]
  (-> (lg/digraph adj)
      (lg/add-nodes* (keys attrs))
      (assoc :attrs attrs)))

(deftest extract-clj
  (is (= (-> (make-digraph
              {:adj {"src/example/foo.clj#L0C4-L0C15" #{"src/example/foo/bar.clj#L0C4-L0C19"
                                                        "src/example/foo/bar.clj#L2C6-L2C7"},
                     "src/example/foo.clj#L3C6-L3C7" #{"src/example/foo/bar.clj#L2C6-L2C7"}}
               :attrs {"src" {:category "Namespace", :label "src", :parent nil},
                       "src/example" {:category "Namespace", :label "example", :parent "src"},
                       "src/example/foo" {:category "Namespace", :label "foo", :parent "src/example"},
                       "src/example/foo.clj" {:category "Namespace", :label "foo.clj", :parent "src/example"},
                       "src/example/foo.clj#L0C4-L0C15" {:label "example.foo", :parent "src/example/foo.clj"},
                       "src/example/foo.clj#L3C6-L3C7" {:label "x", :parent "src/example/foo.clj"},
                       "src/example/foo/bar.clj" {:category "Namespace",
                                                  :label "bar.clj",
                                                  :parent "src/example/foo"},
                       "src/example/foo/bar.clj#L0C4-L0C19" {:label "example.foo.bar",
                                                             :parent "src/example/foo/bar.clj"},
                       "src/example/foo/bar.clj#L2C6-L2C7" {:label "y", :parent "src/example/foo/bar.clj"}}}))
         (let [server (lsp/start-server {:args ["clojure-lsp"]})
               root-path (.getCanonicalPath (io/file "../../test/resources/nested"))]
           (lsp/server-initialize! server {:root-path root-path})
           (relativize-graph root-path
                             (lsp/extract-graph {:root-path root-path
                                                 :source-paths ["src"]
                                                 :server server}))))))
