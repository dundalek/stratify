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
                  nodes)]
    g))

(defn graphviz->dgml [{:keys [digraph flat-namespaces]}]
  (let [g (graphviz->loom {:digraph digraph
                           :flat-namespaces flat-namespaces})
        node-with-children? (->> (lg/nodes g)
                                 (map #(la/attr g % :parent))
                                 set)]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {}
                              (for [node (lg/nodes g)]
                                (xml/element ::dgml/Node
                                             (cond-> {:Id node
                                                      :Label (or (la/attr g node :label)
                                                                 node)
                                                      :Name node}
                                                      ;; add href, color?

                                               (node-with-children? node)
                                               (assoc :Group "Expanded")))))
                 (xml/element ::dgml/Links {}
                              (concat
                               (for [[source target] (lg/edges g)]
                                 (xml/element ::dgml/Link {:Source source :Target target}))
                               (->> (lg/nodes g)
                                    (keep (fn [node-id]
                                            (when-some [parent (la/attr g node-id :parent)]
                                              (xml/element ::dgml/Link {:Source parent :Target node-id :Category "Contains"})))))))
                 (xml/element ::dgml/Styles {}
                              (xml/element ::dgml/Style
                                           {:TargetType "Node"}
                                           (style/property-setter-elements  {:Background (::style/namespace-color theme)
                                                                             :Stroke (::style/namespace-stroke-color theme)
                                                                             :Foreground (::style/node-text-color theme)}))))))

(defn extract [{:keys [input-file output-file flat-namespaces]}]
  (let [digraph (try
                  (theodora/parse (slurp input-file))
                  (catch Throwable t
                    (throw (ex-info "Failed to parse Graphviz file." {:code ::failed-to-parse} t))))
        data (graphviz->dgml {:digraph digraph
                              :flat-namespaces (boolean flat-namespaces)})]

    (sdgml/write-to-file output-file data)))

(comment
  (def digraph (theodora/parse (slurp "test/resources/graphviz/simple.dot")))
  (def digraph (theodora/parse (slurp "test/resources/graphviz/labels.dot")))

  digraph

  (graphviz->dgml {:digraph digraph}))
