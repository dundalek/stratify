(ns io.github.dundalek.stratify.metrics
  (:require
   [io.github.dundalek.stratify.kondo :as kondo]
   [io.github.dundalek.stratify.metrics-dowalil :as metrics-dowalil]
   [io.github.dundalek.stratify.metrics-lakos :as lakos]
   [io.github.dundalek.stratify.metrics-lcom :as metrics-lcom]
   [io.github.dundalek.stratify.metrics-module-depth :as metrics-module-depth]
   [loom.alg :as alg]
   [loom.alg-generic :as algg]
   [loom.graph :as lg])
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

(def lakos-metrics
  {:cumulative-component-dependency lakos/cumulative-component-dependency
   :average-component-dependency lakos/average-component-dependency
   :relative-average-component-dependency lakos/relative-average-component-dependency
   :normalized-cumulative-component-dependency lakos/normalized-cumulative-component-dependency})

(def all-metrics
  (set
   (concat (keys graph-metrics)
           (keys score-metrics))))

(def all-system-metrics
  (set
   (cons
    :num-connected-components
    (keys lakos-metrics))))

(defn wrap-score-metric [f jg]
  (let [scores (.getScores (f jg))]
    #(get scores %)))

(defn wrap-graph-metric [f g]
  #(f g %))

(defn element-metrics
  ([g] (element-metrics g {:metrics all-metrics}))
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
                 [node (reduce
                        (fn [m [metric calc]]
                          (assoc m metric (calc node)))
                        {}
                        calculate)]))
          (into {})))))

(defn system-metrics
  ([g] (system-metrics g {:metrics all-system-metrics}))
  ([g {:keys [metrics]}]
   (reduce
    (fn [m metric-kw]
      (case metric-kw
        :num-connected-components (assoc m metric-kw (count (alg/connected-components g)))
        (if (contains? lakos-metrics metric-kw)
          (assoc m metric-kw ((get lakos-metrics metric-kw) g))
          (do (println "Warning: Unknown metric" metric-kw)
              m))))
    {}
    metrics)))

;; analysis-* metrics are different from graph metrics because they need more information that is present in a graph.
;; For now passing clj-kondo analysis, should come up with more abstract model in the future.

(defn analysis-element-metrics [analysis]
  (merge-with merge
              (metrics-dowalil/relative-visibilities analysis)
              (-> (metrics-lcom/namespaces-connected-components-count analysis)
                  (update-vals (fn [v] {:num-connected-components v})))
              (metrics-module-depth/calculate-depth analysis)))

(defn analysis-system-metrics [analysis]
  (let [visibilities (metrics-dowalil/relative-visibilities analysis)]
    {:average-relative-visibility (double (metrics-dowalil/average-relative-visibility visibilities))
     :global-relative-visibility (double (metrics-dowalil/global-relative-visibility visibilities))}))

(comment
  (def analysis (kondo/analysis ["test/resources/nested/src"]))
  (def analysis (kondo/analysis ["src"]))
  (def analysis (kondo/analysis ["target/projects/asami/src"]))
  (def analysis (kondo/analysis ["target/projects/HumbleUI/src"]))

  (def g (lg/digraph (kondo/->graph analysis)))

  (element-metrics g)

  (system-metrics g))
