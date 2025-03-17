(ns io.github.dundalek.stratify.notebook-delta
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [io.github.dundalek.stratify.dgml :as dgml]
   [io.github.dundalek.stratify.metrics :as metrics]
   [io.github.dundalek.stratify.report :as report]
   [nextjournal.clerk :as clerk]
   [portal.api :as p]
   [portal.viewer :as pv]))

;; # Metrics delta 📈

^{::clerk/visibility {:code :show :result :show}}
(def source-a (first @report/*source-paths))

^{::clerk/visibility {:code :show :result :show}}
(def source-b (second @report/*source-paths))

^::clerk/no-cache
(def graph-a (dgml/load-graph source-a))

^::clerk/no-cache
(def graph-b (dgml/load-graph source-b))

(def metrics-a (metrics/system-metrics graph-a))
(def metrics-b (metrics/system-metrics graph-b))

(def selected-graph-metrics
  [:out-degree
   :in-degree
   :longest-shortest-path
   :transitive-dependencies
   :transitive-dependents])

(def app-viewer
  {:name :portal/app
   :transform-fn
   (fn [value]
     (p/url
      (p/open {:launcher false
               :value    (:nextjournal/value value)
               :theme    :portal.colors/nord-light})))
   :render-fn '#(nextjournal.clerk.viewer/html [:iframe
                                                {:src %
                                                 :style {:width "100%"
                                                         :height "50vh"
                                                         :border-left "1px solid #d8dee9"
                                                         :border-right "1px solid #d8dee9"
                                                         :border-bottom "1px solid #d8dee9"
                                                         :border-radius 2}}])})

(defn open
  "Open portal with the value of `x` in current notebook."
  ([x] (open {} x))
  ([viewer-opts x]
   (clerk/with-viewer app-viewer viewer-opts x)))

{::clerk/visibility {:code :hide :result :show}}

;; ### System metrics delta

(clerk/table
 {:head [:metric :a :b :delta]
  :rows (->> (keys metrics-a)
             (map (fn [metric-kw]
                    (let [a (get metrics-a metric-kw)
                          b (get metrics-b metric-kw)]
                      [metric-kw a b
                       (when (and a b) (- b a))]))))})

;; ### Element metrics diff

(merge
 {:nextjournal/width :full}
 (open
  (pv/diff
   [(metrics/element-metrics graph-a {:metrics selected-graph-metrics})
    (metrics/element-metrics graph-b {:metrics selected-graph-metrics})])))
