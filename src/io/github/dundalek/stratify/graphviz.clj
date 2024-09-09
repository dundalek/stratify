(ns io.github.dundalek.stratify.graphviz
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [loom.attr :as la]
   [loom.graph :as lg]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]
   [io.github.dundalek.stratify.style :as style :refer [theme]]
   [io.github.dundalek.theodora.parser :as parser]
   [dorothy.core :as-alias dc]
   [io.github.dundalek.stratify.internal :as stratify]))

(defn graphviz->dgml [{:keys [digraph flat-namespaces]}]
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
        g (cond-> (lg/digraph adj)
            (not flat-namespaces) (stratify/add-clustered-namespace-hierarchy "/"))
        node-with-children? (->> (lg/nodes g)
                                 (map #(la/attr g % :parent))
                                 set)]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {}
                              (for [node (lg/nodes g)]
                                (xml/element ::dgml/Node
                                             (cond-> {:Id node
                                                      :Label (la/attr g node :label)
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
                                           (stratify/property-setter-elements  {:Background (::style/namespace-color theme)
                                                                                :Stroke (::style/namespace-stroke-color theme)
                                                                                :Foreground (::style/node-text-color theme)}))))))

(defn extract [{:keys [input-file output-file flat-namespaces]}]
  (let [digraph (parser/parse (slurp input-file))
        data (graphviz->dgml {:digraph digraph
                              :flat-namespaces (boolean flat-namespaces)})]
    (if (instance? java.io.Writer output-file)
      (xml/indent data output-file)
      (with-open [out (io/writer output-file)]
        (xml/indent data out)))))

(comment
  (let [digraph (parser/parse (slurp "test/resources/graphviz/simple.dot"))]
    (graphviz->dgml {:digraph digraph})))
