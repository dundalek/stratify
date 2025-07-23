(ns io.github.dundalek.stratify.graphviz
  (:require
   [clojure.data.xml :as xml]
   [dorothy.core :as-alias dc]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.internal :as stratify]
   [io.github.dundalek.stratify.style :as style :refer [theme]]
   [loom.attr :as la]
   [loom.graph :as lg]
   [theodora.core :as theodora]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

(def styles
  [(xml/element ::dgml/Style
                {:TargetType "Node"}
                (style/property-setter-elements  {:Background (::style/namespace-color theme)
                                                  :Stroke (::style/namespace-stroke-color theme)
                                                  :Foreground (::style/node-text-color theme)}))])

(defn graphviz->loom [{:keys [digraph flat-namespaces]}]
  (let [{:keys [statements]} digraph
        nodes (->> statements
                   (filter #(= ::dc/node (:type %))))
        edges (->> statements
                   (filter #(= ::dc/edge (:type %)))
                   (mapcat (fn [{:keys [node-ids]}]
                             (->> node-ids
                                  (map :id)
                                  (partition 2 1)))))
        adj (->> nodes
                 (reduce (fn [m {:keys [id]}]
                           (assoc m (:id id) #{}))
                         {}))
        adj (->> edges
                 (reduce (fn [m [from to]]
                           (update m from (fnil conj #{}) to))
                         adj))
        _ (when (empty? adj)
            (throw (ex-info "Input graph has no nodes or edges." {:code ::empty-graph})))
        g (cond-> (lg/digraph adj)
            (not flat-namespaces) (stratify/add-clustered-namespace-hierarchy "/"))
        g (reduce (fn [g {:keys [id attrs]}]
                    (let [label (get attrs "label")]
                      (cond-> g
                        label (la/add-attr (:id id) :label label))))
                  g
                  nodes)
        g (reduce (fn [g node-id]
                    (cond-> g
                      :always (la/add-attr node-id :Name node-id)
                      (nil? (la/attr g node-id :label)) (la/add-attr node-id :label node-id)))
                  g
                  (lg/nodes g))]
    g))

(defn extract-graph [{:keys [input-file flat-namespaces]}]
  (let [digraph (try
                  (theodora/parse (slurp input-file))
                  (catch Throwable t
                    (throw (ex-info "Failed to parse Graphviz file." {:code ::failed-to-parse} t))))]
    (graphviz->loom {:digraph digraph
                     :flat-namespaces flat-namespaces})))

(defn graph->dgml [g]
  (sdgml/graph->dgml g {:styles styles}))

(defn extract [{:keys [input-file output-file flat-namespaces]}]
  (let [g (extract-graph {:input-file input-file
                          :flat-namespaces flat-namespaces})
        dgml (graph->dgml g)]
    (sdgml/write-to-file output-file dgml)))

(comment
  (def digraph (theodora/parse (slurp "test/resources/graphviz/simple.dot")))
  (def digraph (theodora/parse (slurp "test/resources/graphviz/labels.dot")))

  (-> digraph))
