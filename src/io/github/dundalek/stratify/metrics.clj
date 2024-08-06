(ns io.github.dundalek.stratify.metrics
  (:require
   [io.github.dundalek.stratify.internal :as internal]
   [io.github.dundalek.stratify.metrics-lakos :as lakos]
   [loom.alg :as alg]
   [loom.graph :as lg])
  (:import
   (org.jgrapht.alg.scoring
    ApBetweennessCentrality
    BetweennessCentrality
    ClosenessCentrality
    ClusteringCoefficient
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

(def score-metrics
  ;; https://jgrapht.org/javadoc/org.jgrapht.core/org/jgrapht/alg/scoring/package-summary.html
  {:ap-betweenness-centrality #(new ApBetweennessCentrality %)
   :betweenness-centrality #(new BetweennessCentrality %)
   :closeness-centrality #(new ClosenessCentrality %)
   :clustering-coefficient #(new ClusteringCoefficient %)
   :harmonic-centrality #(new HarmonicCentrality %)
   :katz-centrality #(new KatzCentrality %)
   :page-rank #(new PageRank %)})

   ;; coreness works only on undirected graphs
   ; :coreness #(new Coreness %)

   ; :edge-betweenness-centrality #(new EdgeBetweennessCentrality %)
   ; :eigenvector-centrality #(new EigenvectorCentrality %)

(comment
  (def result (internal/run-kondo ["src"]))
  (def result (internal/run-kondo ["target/projects/HumbleUI/src"]))

  (def g (lg/digraph (internal/->graph (:analysis result))))

  (def per-node-stats
    (let [jg (->jgraph g)
          scores (update-vals score-metrics
                              (fn [f] (.getScores (f jg))))]
      (->> (lg/nodes g)
           (map (fn [node]
                  [node (merge {:in-degree (lg/in-degree g node)
                                :out-degree (lg/out-degree g node)
                                ;; aka "height"
                                ;; this is likely inefficient to traverse for each node separately
                                :longest-shortest-path (count (alg/longest-shortest-path g node))
                                :transitive-dependencies (lakos/count-transitive-dependencies g node)}
                               (update-vals scores (fn [s] (get s node))))]))
           (into {}))))

  (->> (alg/topsort g)
       (map (fn [node]
              [node (get per-node-stats node)])))

  (->> per-node-stats
       ; (sort-by (comp :in-degree val))
       (sort-by (comp :out-degree val))
       (reverse)))
