(ns io.github.dundalek.stratify.codecharta
  (:require
   [babashka.fs :as fs]
   [babashka.process :as ps :refer [shell]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [io.github.dundalek.stratify.internal :as internal]
   [io.github.dundalek.stratify.metrics :as metrics]
   [jsonista.core :as j]
   [loom.graph :as lg]))

(defn- build-hierarchy [file-map]
  (->> file-map
       (reduce (fn [m [filename attrs]]
                 (let [segments (str/split filename #"/")
                       node {:name (last segments)
                             :type "File"
                             :attributes attrs}]
                   (update-in m (butlast segments) update :files (fnil conj []) node)))
               {})))

(defn- transform-tree [[name m]]
  (if (= (:type m) "File")
    m
    {:type "Folder"
     :name name
     :attributes {}
     :children (into
                (->> (dissoc m :files)
                     (mapv transform-tree))
                (:files m))}))

(defn build-tree [file-map]
  (transform-tree ["root" (build-hierarchy file-map)]))

(def ^:private selected-metrics
  [:out-degree
   :in-degree
   :longest-shortest-path
   :transitive-dependencies
   :transitive-dependents
   :betweenness-centrality
   :page-rank])

(def ^:private attributes
  {"out-degree" {:title "Out Degree" :description ""}
   "in-degree" {:title "In Degree" :description ""}
   "longest-shortest-path" {:title "Longest Shortest Path" :description ""}
   "transitive-dependencies" {:title "Transitive Dependencies" :description ""}
   "transitive-dependents" {:title "Transitive Dependents" :description ""}
   "betweenness-centrality" {:title "Betweenness Centrality" :description ""}
   "page-rank" {:title "Page Rank" :description ""}})

(def ^:private attribute-types
  (->> (keys attributes)
       (map (fn [k]
              [k "absolute"]))
       (into {})))

(defn ->codecharta [{:keys [analysis transform-filename]
                     :or {transform-filename identity}}]
  (let [g (lg/digraph (internal/->graph analysis))
        metrics (metrics/metrics g {:metrics selected-metrics})
        ns->file (->> analysis
                      :namespace-definitions
                      (map (fn [{:keys [name filename]}]
                             [(str name) (transform-filename filename)]))
                      (into {}))
        root (->> metrics
                  (map (fn [{:keys [id] :as attrs}]
                         [(ns->file id) (dissoc attrs :id)]))
                  (into {})
                  (build-tree))]
    {:checksum ""
     :data {:nodes [root]
            :edges []
            :projectName ""
            :attributeDescriptors attributes
            :attributeTypes {:edges {}
                             :nodes attribute-types}
            :apiVersion "1.3"}}))

(defn extract [{:keys [repo-path source-paths output-prefix]}]
  (fs/with-temp-dir [tmp-dir {}]
    (let [suffix-uncompressed ".cc.json"
          suffix-compressed ".cc.json.gz"
          tokei-prefix (str tmp-dir "/tokei")
          tokei-file (str tokei-prefix suffix-compressed)
          gitlog-prefix (str tmp-dir "/gitlog")
          gitlog-file (str gitlog-prefix suffix-compressed)
          kondo-prefix (str tmp-dir "/kondo")
          kondo-file (str kondo-prefix suffix-uncompressed)
          repo-source-paths (->> source-paths (map #(str repo-path "/" %)))
          repo-prefix (str repo-path "/")
          ccsh-bin "ccsh"]

      (try
        (run! ps/check (ps/pipeline
                        (apply ps/pb "tokei -o json" repo-source-paths)
                        (ps/pb ccsh-bin "tokeiimporter" "-r" repo-path "-o" tokei-prefix)))
        (catch Exception e
          (println "Failed to run tokei:" (ex-message e))))

      (try
        (shell ccsh-bin "gitlogparser" "repo-scan" "--repo-path" repo-path "-o" gitlog-prefix)
        (catch Exception e
          (println "Failed to run gitlogparser:" (ex-message e))))

      (try
        (j/write-value (io/file kondo-file)
                       (->codecharta {:analysis (:analysis (internal/run-kondo repo-source-paths))
                                      :transform-filename #(str/replace-first % repo-prefix "")}))
        (catch Exception e
          (println "Failed to run kondo:" (ex-message e))))

      (let [files-to-merge (->> [["tokei" tokei-file]
                                 ["gitlog" gitlog-file]
                                 ["kondo" kondo-file]]
                                (filter (comp fs/exists? second)))]
        (println "Merging sources:" (->> files-to-merge (map first) (str/join ", ")))
        (apply shell ccsh-bin "merge" "--leaf" "-f" "-o" output-prefix (map second files-to-merge))))))

(comment
  (def result (internal/run-kondo ["test/resources/nested/src"]))
  (def result (internal/run-kondo ["src"]))

  (j/write-value (io/file "metrics.cc.json")
                 (->codecharta {:analysis (:analysis result)}))

  (extract {:repo-path "."
            :source-paths ["src"]
            :output-prefix "stratify"}))
