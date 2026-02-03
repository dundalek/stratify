(ns io.github.dundalek.stratify.test-utils
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]
   [clojure.test :refer [is testing]]
   [loom.graph :as lg]))

(defn is-same? [path]
  (let [result (sh "git" "status" "--porcelain" path)]
    (testing path
      (is (= 0 (:exit result)))
      (is (= "" (:err result)))
      (is (= [""] (str/split-lines (:out result)))))))

(defmacro thrown-error-code [& body]
  `(try
     ~@body
     nil
     (catch clojure.lang.ExceptionInfo e#
       (:code (ex-data e#)))))

(defn relativize-graph [root-path g]
  (let [uri-base (str "file://" root-path "/")
        transform-id #(some-> % (str/replace-first uri-base ""))]
    (-> (lg/digraph)
        (lg/add-nodes* (->> (lg/nodes g) (map transform-id)))
        (lg/add-edges* (->> (lg/edges g) (map (fn [[source target]]
                                                [(transform-id source) (transform-id target)]))))
        (assoc :attrs (reduce-kv
                       (fn [m k v]
                         (assoc m
                                (transform-id k)
                                (update v :parent transform-id)))
                       {}
                       (:attrs g))))))

(defn extract-relative-graph [extract-fn path]
  (relativize-graph (.getCanonicalPath (io/file path))
                    (extract-fn {:root-path path})))

(defn make-digraph [{:keys [adj attrs]}]
  (-> (lg/digraph adj)
      (lg/add-nodes* (keys attrs))
      (assoc :attrs attrs)))
