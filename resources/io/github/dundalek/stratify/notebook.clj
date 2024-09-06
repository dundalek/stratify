{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(ns io.github.dundalek.stratify.notebook
  (:require
   [io.github.dundalek.stratify.internal :as internal]
   [io.github.dundalek.stratify.metrics :as metrics]
   [loom.graph :as lg]
   [nextjournal.clerk :as clerk]
   [fastmath.stats :as stats]))

(defn table-with-colums [data columns]
  (clerk/table
   {:head columns
    :rows (->> data
               (map (apply juxt columns)))}))

;; # Code Metrics 📊

;; Project sources

^{::clerk/visibility {:code :hide :result :show}}
@metrics/*source-paths

(def result (internal/run-kondo @metrics/*source-paths))
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

;; [Stats details](https://generateme.github.io/fastmath/notebooks/notebooks/stats/#list-of-symbols) in the Descriptive statistics section.

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

;; Outliers

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

;; Metric details

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

;; Height https://en.wikipedia.org/wiki/Glossary_of_graph_theory#height
;; smaller height - flatter

(clerk/col
 (for [metric selected-metrics]
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
                 :fill {:value "#4269D0"}}}))))
