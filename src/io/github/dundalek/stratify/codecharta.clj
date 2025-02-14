(ns io.github.dundalek.stratify.codecharta
  (:require
   [babashka.fs :as fs]
   [babashka.process :as ps :refer [shell]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [io.github.dundalek.stratify.codecov :as codecov]
   [io.github.dundalek.stratify.kondo :as kondo]
   [io.github.dundalek.stratify.metrics :as metrics]
   [jsonista.core :as j]
   [loom.graph :as lg]))

(def ^:dynamic *ccsh-bin* "ccsh")

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

(defn- naive-snake-case [s]
  (assert (re-matches #"[-a-z]+" s))
  (str/replace s #"-" "_"))

(def ^:private metrics-attributes
  {:out-degree {:title "Out Degree" :description ""}
   :in-degree {:title "In Degree" :description ""}
   :longest-shortest-path {:title "Longest Shortest Path" :description ""}
   :transitive-dependencies {:title "Transitive Dependencies" :description ""}
   :transitive-dependents {:title "Transitive Dependents" :description ""}
   :betweenness-centrality {:title "Betweenness Centrality" :description ""}
   :page-rank {:title "Page Rank" :description ""}})

(def ^:private attribute-key->str
  (memoize (fn [kw]
             (str (when (contains? metrics-attributes kw) "graph_")
                  (naive-snake-case (name kw))))))

;; `direction` property specifies whether higher or lower attribute values indicate better code quality:
;; - `-1` lower is better
;; - `1`  higher is better
(def ^:private attributes
  (-> metrics-attributes
      (update-vals #(assoc % :direction -1))
      (assoc :line-coverage {:title "Line Coverage" :description "" :direction 1})))

;; The difference between types is how aggregates get shown when hovering over a folder:
;; - "absolute" as sum
;; - "relative" as median
(def ^:private attribute-types
  (merge
   (update-vals attributes (constantly "absolute"))
   {:betweenness-centrality "relative"
    :page-rank "relative"
    :line-coverage "relative"}))

(defn- calculate-metrics [{:keys [analysis transform-filename line-coverage]
                           :or {transform-filename identity}}]
  (let [g (lg/digraph (kondo/->graph analysis))
        metrics (metrics/element-metrics g {:metrics (keys metrics-attributes)})
        ns->file (->> analysis
                      :namespace-definitions
                      (map (fn [{:keys [name filename]}]
                             [(str name) (transform-filename filename)]))
                      (into {}))]
    (->> metrics
         (map (fn [[id attrs]]
                (let [filename (ns->file id)
                      attrs (cond-> attrs
                              line-coverage (assoc :line-coverage (line-coverage filename)))]
                  [filename (update-keys attrs attribute-key->str)])))
         (into {}))))

(defn ->codecharta [opts]
  {:checksum ""
   :data {:nodes [(build-tree (calculate-metrics opts))]
          :edges []
          :projectName ""
          :attributeDescriptors (update-keys attributes attribute-key->str)
          :attributeTypes {:edges {}
                           :nodes (update-keys attribute-types attribute-key->str)}
          :apiVersion "1.3"}})

(defn extract-clj [{:keys [repo-path source-paths coverage-file output-file]}]
  (let [repo-prefix (str repo-path "/")
        repo-source-paths (->> source-paths (map #(str repo-prefix %)))
        line-coverage (when coverage-file
                        (codecov/make-line-coverage-lookup {:coverage-file coverage-file
                                                            :strip-prefixes source-paths}))]

    (j/write-value (io/file output-file)
                   (->codecharta {:analysis (kondo/analysis repo-source-paths)
                                  :transform-filename #(str/replace-first % repo-prefix "")
                                  :line-coverage line-coverage}))))

(defn extract [{:keys [repo-path source-paths output-prefix coverage-file]}]
  (fs/with-temp-dir [tmp-dir {}]
    (let [suffix-uncompressed ".cc.json"
          suffix-compressed ".cc.json.gz"
          tokei-prefix (str tmp-dir "/tokei")
          tokei-file (str tokei-prefix suffix-compressed)
          gitlog-prefix (str tmp-dir "/gitlog")
          gitlog-file (str gitlog-prefix suffix-compressed)
          kondo-prefix (str tmp-dir "/kondo")
          kondo-file (str kondo-prefix suffix-uncompressed)
          repo-source-paths (->> source-paths (map #(str repo-path "/" %)))]

      (try
        (run! ps/check (ps/pipeline
                        (apply ps/pb "tokei -o json" repo-source-paths)
                        (ps/pb *ccsh-bin* "tokeiimporter" "-r" repo-path "-o" tokei-prefix)))
        (catch Exception e
          (println "Failed to run tokei:" (ex-message e))))

      (try
        (shell *ccsh-bin* "gitlogparser" "repo-scan" "--repo-path" repo-path "-o" gitlog-prefix)
        (catch Exception e
          (println "Failed to run gitlogparser:" (ex-message e))))

      (try
        (extract-clj {:repo-path repo-path
                      :source-paths source-paths
                      :coverage-file coverage-file
                      :output-file kondo-file})
        (catch Exception e
          (println "Failed to run kondo:" (ex-message e))))

      (let [files-to-merge (->> [["tokei" tokei-file]
                                 ["gitlog" gitlog-file]
                                 ["kondo" kondo-file]]
                                (filter (comp fs/exists? second)))]
        (println "Merging sources:" (->> files-to-merge (map first) (str/join ", ")))
        (try
          (apply shell *ccsh-bin* "merge" "--leaf" "-f" "-o" output-prefix (map second files-to-merge))
          (catch Throwable t
            (if (and (instance? java.io.IOException t)
                     (str/starts-with? (ex-message (ex-cause t)) "error=2,")) ; No such file or directory
              (throw (ex-info (str "Failed to run `ccsh`.\n"
                                   "Please make sure to have CodeCharta CLI installed.\n"
                                   "Suggestion: `npm i -g codecharta-analysis`.")
                              {:code ::ccsh-not-found} t))
              (throw (ex-info "Failed to run `ccsh`."
                              {:code ::ccsh-failed-to-run} t)))))))))

(comment
  (def analysis (kondo/analysis ["test/resources/nested/src"]))
  (def analysis (kondo/analysis ["src"]))

  (j/write-value (io/file "metrics.cc.json")
                 (->codecharta {:analysis analysis}))

  (extract {:repo-path "."
            :source-paths ["src"]
            :output-prefix "stratify"})

  (def coverage-file "target/coverage/codecov.json")

  (extract {:repo-path "."
            :source-paths ["src"]
            :output-prefix "stratify"
            :coverage-file coverage-file})

  (extract-clj
   {:repo-path "."
    :source-paths ["src"]
    :coverage-file coverage-file
    :output-file "stratify-clj.cc.json"})

  (->codecharta {:analysis analysis})

  (calculate-metrics
   {:analysis analysis
    :line-coverage (some-> coverage-file codecov/make-line-coverage-raw-lookup)}))
