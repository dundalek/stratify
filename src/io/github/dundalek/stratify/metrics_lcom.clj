(ns io.github.dundalek.stratify.metrics-lcom
  (:require
   [io.github.dundalek.stratify.kondo :as kondo]
   [loom.alg :as alg]
   [loom.graph :as lg]))

;; https://www.aivosto.com/project/help/pm-oo-cohesion.html

(defn namespace-usages->graph [usages]
  (let [edges (->> usages
                   (keep (fn [{:keys [from to from-var name]}]
                           (when (= from to)
                             [from-var name]))))]
    (-> (lg/digraph)
        (lg/add-edges* edges))))

(defn namespaces-connected-components-count
  "LCOM4 is number of connected components."
  [analysis]
  (->> analysis
       :var-usages
       (group-by :from)
       (map (fn [[ns-name usages]]
              [(str ns-name)
               (count (alg/connected-components (namespace-usages->graph usages)))]))
       (into {})))

(comment
  (def analysis (kondo/analysis ["test/resources/code/clojure/connected-components/src"]))
  (def analysis (kondo/analysis ["src"]))

  (namespaces-connected-components-count analysis))
