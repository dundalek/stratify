{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(ns io.github.dundalek.stratify.notebook
  (:require
   [fastmath.stats :as stats]
   [io.github.dundalek.stratify.internal :as internal]
   [io.github.dundalek.stratify.kondo :as kondo]
   [io.github.dundalek.stratify.metrics :as metrics]
   [io.github.dundalek.stratify.report :as report]
   [loom.graph :as lg]
   [nextjournal.clerk :as clerk]))

(defn table-with-colums [data columns]
  (clerk/table
   {:head columns
    :rows (->> data
               (map (apply juxt columns)))}))

;; # Code Metrics 📊

;; Project sources

^{::clerk/visibility {:code :hide :result :show}}
@report/*source-paths

;; ## Metrics

(def result (kondo/run-kondo @report/*source-paths))
(def g (lg/digraph (internal/->graph (:analysis result))))

(def selected-metrics
  [:out-degree
   :in-degree
   :longest-shortest-path
   :transitive-dependencies
   :transitive-dependents
   :betweenness-centrality
   :page-rank
   #_:ap-betweenness-centrality
   #_:closeness-centrality
   #_:clustering-coefficient
   #_:harmonic-centrality
   #_:katz-centrality])

(def metrics (metrics/metrics g {:metrics selected-metrics}))
(def stats
  (->> selected-metrics
       (map (fn [metric-kw]
              (assoc (stats/stats-map (map metric-kw metrics))
                     :id metric-kw)))))

{::clerk/visibility {:code :hide :result :show}}

;; Namespace metrics

(table-with-colums
 (->> metrics (sort-by :id))
 (cons :id selected-metrics))

;; ## Statistics

;; See [statistics legend](https://generateme.github.io/fastmath/notebooks/notebooks/stats/#list-of-symbols) (Descriptive statistics section).

;; Basic stats

(table-with-colums
 stats
 [:id
  #_:Size
  :Min
  :Max
  :Range
  :Mean
  :Median
  :Mode
  :Q1
  :Q3
  :Total])

;; Additional stats

(table-with-colums
 stats
 [:id
  :SD
  :Variance
  :MAD
  :SEM
  :LAV
  :UAV
  :IQR
  :LOF
  :UOF
  :LIF
  :UIF
  :Outliers
  :Kurtosis
  :Skewness])

;; ## Outliers

;; There are no absolute values we can claim for a metric to be good or bad.
;; One approach can be to look for candidates for improvement within outliers, that stand out from other modules in a system.
;; Outliers are defined as [values outside inner fences](https://generateme.github.io/fastmath/notebooks/notebooks/stats/#LOS-outliers).

(let [outliers (->> stats
                    (filter (comp seq :Outliers))
                    (mapcat (fn [{:keys [id Outliers]}]
                              (let [outlier-vals (set Outliers)]
                                (->> metrics
                                     (keep (fn [metric]
                                             (when (contains? outlier-vals (double (id metric)))
                                               {:id (:id metric)
                                                :metric id
                                                :value (id metric)})))))))
                    (group-by :id)
                    (map (fn [[id values]]
                           [id (count values) (->> values
                                                   (map (juxt :metric :value))
                                                   (into {}))]))
                    (sort-by (comp - second)))]
  (if (seq outliers)
    (clerk/table
     {:head ["id" "count" "metrics"]
      :rows outliers})
    "No outliers detected."))

;; ## Charts

;; ### Combined Out-Degree and In-Degree

(clerk/vl
 {:data {:values (->> metrics
                      (sort-by (fn [{:keys [out-degree in-degree]}]
                                 (- (+ out-degree in-degree))))
                      (map-indexed (fn [i m] (assoc m :order i))))}
  :repeat {:layer ["out-degree" "in-degree"]}
  :spec {:mark "bar"
         :encoding {:x {:field "id" :type "nominal"
                        :axis {:labelAngle 35}
                        :sort {:field "order"}}
                    :y {:field {:repeat "layer"}
                        :type "quantitative"
                        :title "Degree"}
                    :color {:datum {:repeat "layer"}
                            :title "Direction"}
                    :xOffset {:datum {:repeat "layer"}}}}
  :config {:mark {:invalid nil}
           :legend {:orient "bottom"}
           :range {:category {:scheme "observable10"}}}})

^{::clerk/visibility {:code :hide :result :hide}}
(defn metric-graphs [metric]
  (clerk/col
   (clerk/vl
    {:description ""
     :data {:values metrics}
     :mark "bar"
     :encoding {:x {:field (name metric)
                     ; :type "ordinal"
                     ; :bin true
                    :bin (contains? #{:betweenness-centrality :page-rank} metric)}
                :y {:aggregate "count"}
                  ;; first color of https://vega.github.io/vega/docs/schemes/#observable10
                :fill {:value "#4269D0"}}})
   (clerk/vl
    {:description ""
     :data {:values metrics}
     :mark "bar"
     :encoding {:x {:field "id" :type "nominal" :axis {:labelAngle 35}
                    :sort "-y"}
                :y {:field (name metric) :type "quantitative"}
                  ;; first color of https://vega.github.io/vega/docs/schemes/#observable10
                :fill {:value "#4269D0"}}})))

#_(clerk/col
   ;; All graphs
   (for [metric selected-metrics]
     (metric-graphs metric)))

;; ### Out Degree

;; [Out Degree](https://en.wikipedia.org/wiki/Out_degree) is a number of outgoing edges, in other words a number of direct dependencies.

(metric-graphs :out-degree)

;; ### In Degree

;; [In Degree](https://en.wikipedia.org/wiki/In_degree) is a number of incoming edges (number of direct dependents).

(metric-graphs :in-degree)

;; ### Longest Shortest Path (Height)

;; Calculates number of edges needed to traverse to each node from a given starting node and picks the longest path.

;; The largest value is also known as [graph height](https://en.wikipedia.org/wiki/Glossary_of_graph_theory#height).
;; Large height might indicate the structure is too nested and might be worth to try to "flatten" the hierarchy.

(metric-graphs :longest-shortest-path)

;; ### Transitive Dependencies

(metric-graphs :transitive-dependencies)

;; ### Transitive Dependents

(metric-graphs :transitive-dependents)

;; ### Betweenness Centrality

;; Computes a [centrality](https://en.wikipedia.org/wiki/Betweenness_centrality) of nodes.
;; Large value indicates hubs.
;; It might be worth to consider splitting such modules into smaller ones.

(metric-graphs :betweenness-centrality)

;; ### Page Rank

;; [PageRank](https://en.wikipedia.org/wiki/PageRank) is another measure indicating importance of nodes. Problems with such modules will have larger impact on other modules.

(metric-graphs :page-rank)
