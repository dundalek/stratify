(ns io.github.dundalek.stratify.treesitter-lua
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [io.github.dundalek.stratify.internal :as internal]
   [loom.attr :as la]
   [loom.graph :as lg])
  (:import
   (org.treesitter TSParser TreeSitterLua)))

(defn- find-nodes-by-type [tree type]
  (let [results (atom [])]
    (letfn [(walk [node]
              (when (= (.getType node) type)
                (swap! results conj node))
              (dotimes [i (.getChildCount node)]
                (walk (.getChild node i))))]
      (walk (.getRootNode tree)))
    @results))

(defn- find-child-by-type [node type]
  (let [n (.getChildCount node)]
    (loop [i 0]
      (when (< i n)
        (let [child (.getChild node i)]
          (if (= (.getType child) type)
            child
            (recur (inc i))))))))

(defn- node-text [node ^bytes source-bytes]
  (String. source-bytes (.getStartByte node) (- (.getEndByte node) (.getStartByte node)) "UTF-8"))

(defn- find-child-by-type-recursive [node type]
  (let [n (.getChildCount node)]
    (loop [i 0]
      (when (< i n)
        (let [child (.getChild node i)]
          (if (= (.getType child) type)
            child
            (or (find-child-by-type-recursive child type)
                (recur (inc i)))))))))

(defn- extract-require-path [node source-bytes]
  (when-let [string-node (find-child-by-type-recursive node "string")]
    (let [text (node-text string-node source-bytes)]
      (subs text 1 (dec (count text))))))

(defn- require-call? [node source-bytes]
  (and (= (.getType node) "call")
       (when-let [var-node (find-child-by-type node "variable")]
         (when-let [name-node (find-child-by-type var-node "identifier")]
           (= (node-text name-node source-bytes) "require")))))

(defn- extract-require-from-local-decl
  "Handles tree-sitter-lua grammar bug where `local x = require(...)`
   produces ERROR nodes instead of proper call nodes."
  [node source-bytes]
  (when-let [error-node (find-child-by-type node "ERROR")]
    (when-let [id-node (find-child-by-type error-node "identifier")]
      (when (= (node-text id-node source-bytes) "require")
        (when-let [expr-list (find-child-by-type node "expression_list")]
          (when-let [string-node (find-child-by-type expr-list "string")]
            (let [text (node-text string-node source-bytes)]
              (subs text 1 (dec (count text))))))))))

(defn- find-require-paths [tree source-bytes]
  (concat
   (->> (find-nodes-by-type tree "call")
        (filter #(require-call? % source-bytes))
        (keep #(extract-require-path % source-bytes)))
   (->> (find-nodes-by-type tree "local_variable_declaration")
        (keep #(extract-require-from-local-decl % source-bytes)))))

(defn- normalize-opts [opts]
  (update opts :root-path #(.getCanonicalPath (io/file %))))

(defn extract-lua [{:keys [root-path source-paths source-pattern]
                    :or {source-paths ["."]
                         source-pattern "**.lua"}}]
  (let [opts (normalize-opts {:root-path root-path})
        root-path (:root-path opts)
        uri-base (str "file://" root-path "/")
        parser (TSParser.)
        _ (.setLanguage parser (TreeSitterLua.))
        file-paths (->> source-paths
                        (mapcat #(fs/glob (fs/file root-path %) source-pattern)))
        file-uris (mapv #(str "file://" %) file-paths)
        file-uris-set (set file-uris)
        file-analyses (for [file-path file-paths
                            :let [uri (str "file://" file-path)
                                  source (slurp (str file-path))
                                  source-bytes (.getBytes source "UTF-8")
                                  tree (.parseString parser nil source)]]
                        {:uri uri
                         :require-paths (find-require-paths tree source-bytes)})
        require-path->uri (into {}
                                (for [{:keys [uri]} file-analyses
                                      :let [relative (str/replace-first uri uri-base "")
                                            path-without-ext (str/replace relative #"\.lua$" "")
                                            ;; Also strip first directory (source root like "lua/")
                                            path-without-root (str/replace-first path-without-ext #"^[^/]+/" "")
                                            dot-path (str/replace path-without-root "/" ".")]
                                      require-path [path-without-ext dot-path]]
                                  [require-path uri]))
        edges (for [{:keys [uri require-paths]} file-analyses
                    path require-paths
                    :let [target-uri (get require-path->uri path)]
                    :when (and target-uri (contains? file-uris-set target-uri))]
                [uri target-uri])
        g (-> (lg/digraph)
              (lg/add-nodes* file-uris)
              (internal/add-clustered-namespace-hierarchy-path-based uri-base))
        g (reduce (fn [g node]
                    (la/add-attr g node :category "Namespace"))
                  g
                  (lg/nodes g))]
    (lg/add-edges* g edges)))
