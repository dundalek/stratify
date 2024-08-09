(ns io.github.dundalek.stratify.metrics
  (:require
   [clojure.java.io :as io]
   [io.github.dundalek.stratify.internal :as internal]
   [io.github.dundalek.stratify.metrics-lakos :as lakos]
   [loom.alg :as alg]
   [loom.alg-generic :as algg]
   [loom.graph :as lg]
   [nextjournal.clerk :as clerk])
  (:import
   (org.jgrapht.alg.scoring
    ApBetweennessCentrality
    BetweennessCentrality
    ClosenessCentrality
    ClusteringCoefficient
    EdgeBetweennessCentrality
    EigenvectorCentrality
    HarmonicCentrality
    KatzCentrality
    PageRank)
   (org.jgrapht.graph DefaultDirectedGraph DefaultEdge)))

(defn ->jgraph [loom-graph]
  (let [g (DefaultDirectedGraph. DefaultEdge)]
    (doseq [node (lg/nodes loom-graph)]
      (.addVertex g node))
    (doseq [[from to] (lg/edges loom-graph)]
      (.addEdge g from to))
    g))

(defn count-transitive-dependents [g node]
  (count (algg/post-traverse #(lg/predecessors g %) node)))

(defn longest-shortest-path [g node]
  (count (alg/longest-shortest-path g node)))

(def graph-metrics
  {:in-degree lg/in-degree
   :out-degree lg/out-degree
   ;; aka "height"
   ;; it would probably be more efficient to use all pairs traversing instead traversing for each node separately
   :longest-shortest-path longest-shortest-path
   :transitive-dependencies lakos/count-transitive-dependencies
   :transitive-dependents count-transitive-dependents})

(def score-metrics
  ;; https://jgrapht.org/javadoc/org.jgrapht.core/org/jgrapht/alg/scoring/package-summary.html
  {:ap-betweenness-centrality #(new ApBetweennessCentrality %)
   :betweenness-centrality #(new BetweennessCentrality %)
   :closeness-centrality #(new ClosenessCentrality %)
   :clustering-coefficient #(new ClusteringCoefficient %)
   :harmonic-centrality #(new HarmonicCentrality %)
   :katz-centrality #(new KatzCentrality %)
   :page-rank #(new PageRank %)
   :edge-betweenness-centrality #(new EdgeBetweennessCentrality %)
   :eigenvector-centrality #(new EigenvectorCentrality %)})
   ;; coreness works only on undirected graphs
   ; :coreness #(new Coreness %)

(def all-metrics
  (concat (keys graph-metrics)
          (keys score-metrics)))

(defn wrap-score-metric [f jg]
  (let [scores (.getScores (f jg))]
    #(get scores %)))

(defn wrap-graph-metric [f g]
  #(f g %))

(defn metrics
  ([g] (metrics g {:metrics all-metrics}))
  ([g {:keys [metrics]}]
   (let [jg (->jgraph g)
         calculate (reduce (fn [m metric-kw]
                             (cond
                               (contains? score-metrics metric-kw)
                               (assoc m metric-kw (wrap-score-metric (get score-metrics metric-kw) jg))

                               (contains? graph-metrics metric-kw)
                               (assoc m metric-kw (wrap-graph-metric (get graph-metrics metric-kw) g))

                               :else
                               (do (println "Warning: Unknown metric" metric-kw)
                                   m)))
                           {}
                           metrics)]
     (->> (lg/nodes g)
          (map (fn [node]
                 (reduce
                  (fn [m [metric calc]]
                    (assoc m metric (calc node)))
                  {:id node}
                  calculate)))))))

;; Atom to pass paths from args to the notebook
(defonce *source-paths (atom []))

(defn report! [{:keys [source-paths output-path]}]
  (let [notebook-path (.getCanonicalPath (io/file (io/resource "io/github/dundalek/stratify/notebook.clj")))]
    (reset! *source-paths source-paths)
    (if output-path
      (clerk/build! {:index notebook-path
                     :package :single-file
                     :out-path output-path})
      (clerk/serve! {:index notebook-path
                     :browse true}))))

(comment
  (reset! *source-paths ["src"])
  (reset! *source-paths ["target/projects/asami/src"])
  (reset! *source-paths ["target/projects/HumbleUI/src"])

  (def result (internal/run-kondo @*source-paths))
  (def g (lg/digraph (internal/->graph (:analysis result))))

  (metrics g)

  (->> (metrics g)
       ; (sort-by :in-degree)
       (sort-by :out-degree)
       (reverse))

  (clerk/serve! {:browse true
                 :port 7788
                 :index "notebooks/io/github/dundalek/stratify/notebook.clj"
                 :watch-paths ["src" "notebooks"]}))
