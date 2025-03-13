(ns io.github.dundalek.stratify.gabotechs-dep-tree
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [io.github.dundalek.stratify.kondo :as kondo]
   [jsonista.core :as j]
   [loom.graph :as lg]))

(def max-node-size 10)

(defn ->graph [analysis]
  (let [g (lg/digraph (kondo/->graph analysis))
        {:keys [namespace-definitions]} analysis
        namespace-info (->> namespace-definitions
                            (map (fn [{:keys [name filename]}]
                                   [(str name) {:filename filename
                                                :loc (count (line-seq (io/reader filename)))}]))
                            (into {}))
        max-loc (->> namespace-info vals (map :loc) (reduce max))
        node->id (->> (lg/nodes g)
                      (map-indexed (fn [i id] [id (inc i)]))
                      (into {}))
        nodes (->> (lg/nodes g)
                   (keep (fn [node]
                           (let [{:keys [filename loc] :or {filename "" loc 0}} (namespace-info node)]
                             {:id (node->id node)
                              ; :isEntrypoint false
                              :fileName (fs/file-name filename)
                              :pathBuf (str/split filename #"/")
                              ; :group ""
                              :dirName (str (fs/parent filename) "/")
                              :loc loc
                              :size (int (* max-node-size (/ loc max-loc)))}))))
        links (->> (lg/edges g)
                   (map (fn [[from to]]
                          {:from (node->id from)
                           :to (node->id to)})))]
                           ; :isCyclic false})))]
    {:nodes nodes
     :links links}))
     ; :enabledGui false}))

(defn extract [{:keys [source-paths output-file]}]
  (let [data (->graph (kondo/analysis source-paths))]
    (j/write-value (io/file output-file) data)))

(comment
  (extract {:source-paths ["src"]
            :output-file "target/dep-tree.json"}))
