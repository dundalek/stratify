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

(defn- extract-relative-graph [extract-fn path]
  (let [root-path (.getCanonicalPath (io/file path))]
    (relativize-graph root-path
                      (extract-fn {:root-path root-path}))))

(defn- make-digraph [{:keys [adj attrs]}]
  (-> (lg/digraph adj)
      (lg/add-nodes* (keys attrs))
      (assoc :attrs attrs)))

(deftest extract-clojure
  (is (= (make-digraph
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
                   "src/example/foo/bar.clj#L2C6-L2C7" {:label "y", :parent "src/example/foo/bar.clj"}}})
         (extract-relative-graph lsp/extract-clojure "../../test/resources/nested"))))

(deftest extract-go
  (is (= (make-digraph
          {:adj {"main.go#L7C5-L7C9" #{"greet/greet.go#L2C5-L2C13"}},
           :attrs {"greet" {:category "Namespace", :label "greet", :parent nil},
                   "greet/greet.go" {:category "Namespace", :label "greet.go", :parent "greet"},
                   "greet/greet.go#L2C5-L2C13" {:label "TheWorld", :parent "greet/greet.go"},
                   "main.go" {:category "Namespace", :label "main.go", :parent nil},
                   "main.go#L7C5-L7C9" {:label "main", :parent "main.go"}}})
         (extract-relative-graph lsp/extract-go "../scip/test/resources/sample-go"))))

(deftest extract-rust
  (is (= (make-digraph
          {:adj {"src/main.rs#L2C3-L2C7" #{"src/greeting.rs#L0C7-L0C12" "src/main.rs#L0C4-L0C12"}},
           :attrs {"src" {:category "Namespace", :label "src", :parent nil},
                   "src/greeting.rs" {:category "Namespace", :label "greeting.rs", :parent "src"},
                   "src/greeting.rs#L0C7-L0C12" {:label "greet", :parent "src/greeting.rs"},
                   "src/main.rs" {:category "Namespace", :label "main.rs", :parent "src"},
                   "src/main.rs#L0C4-L0C12" {:label "greeting", :parent "src/main.rs"},
                   "src/main.rs#L2C3-L2C7" {:label "main", :parent "src/main.rs"}}})
         (extract-relative-graph lsp/extract-rust "../scip/test/resources/sample-rs"))))
