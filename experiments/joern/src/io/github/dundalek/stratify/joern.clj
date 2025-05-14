(ns io.github.dundalek.stratify.joern
  (:require
   [clojure.data.xml :as xml]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.style :as style]
   [loom.attr :as la]
   [loom.graph :as lg]
   [xmlns.http%3A%2F%2Fgraphml.graphdrawing.org%2Fxmlns :as-alias graphml]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

(defn- add-attrs [g node-or-edge attrs]
  (reduce (fn [g [k v]]
            (la/add-attr g node-or-edge k v))
          g attrs))

(defn- parse-data-elements [el]
  (->> (:content el)
       (filter :tag)
       (map (fn [data-el]
              (assert (= (:tag data-el) ::graphml/data))
              (assert (= (keys (:attrs data-el)) [:key]))
              ;; for example node__CALL__SIGNATURE does not contain content, therefore accepting count 0
              (assert (#{1 0} (count (:content data-el))) (pr-str (:content data-el)))
              [(-> data-el :attrs :key keyword)
               (-> data-el :content first)]))
       (into {})))

(def ^:private node->label-fn
  {"BINDING" :node__BINDING__NAME
   "BLOCK" (fn [node] (str (:node__BLOCK__LINE_NUMBER node) ":" (:node__BLOCK__COLUMN_NUMBER node)))
   "CALL" :node__CALL__NAME
   "CONTROL_STRUCTURE" :node__CONTROL_STRUCTURE__CONTROL_STRUCTURE_TYPE
   "DEPENDENCY" :node__DEPENDENCY__NAME
   "FILE" :node__FILE__NAME
   "IDENTIFIER" :node__IDENTIFIER__NAME
   "IMPORT" :node__IMPORT__IMPORTED_ENTITY
   "LITERAL" :node__LITERAL__TYPE_FULL_NAME
   "LOCAL" :node__LOCAL__NAME
   "META_DATA" :node__META_DATA__ROOT
   "METHOD" :node__METHOD__NAME
   "METHOD_PARAMETER_IN" :node__METHOD_PARAMETER_IN__NAME
   "METHOD_PARAMETER_OUT" :node__METHOD_PARAMETER_OUT__NAME
   "METHOD_REF" :node__METHOD_REF__METHOD_FULL_NAME
   ; "METHOD_RETURN" :labelV
   "NAMESPACE" :node__NAMESPACE__NAME
   "NAMESPACE_BLOCK" :node__NAMESPACE_BLOCK__FULL_NAME
   "TYPE" :node__TYPE__NAME
   "TYPE_DECL" :node__TYPE_DECL__NAME})

(defn- node-label [node]
  (let [{:keys [labelV]} node
        f (node->label-fn labelV)]
    (cond-> labelV
      f (str " " (f node)))))

(defn- graph->all-dgml [g]
  (xml/element ::dgml/DirectedGraph
               {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
               (xml/element ::dgml/Nodes {}
                            (for [node (lg/nodes g)]
                              (let [attrs (la/attrs g node)]
                                (xml/element ::dgml/Node
                                             (merge attrs
                                                    {:Id node
                                                     :Label (node-label attrs)
                                                     :Category (:labelV attrs)})))))
               (xml/element ::dgml/Links {}
                            (for [edge (lg/edges g)]
                              (let [[source target] edge]
                                (xml/element ::dgml/Link (merge {:Source source :Target target}
                                                                (la/attrs g edge))))))
               #_(xml/element ::dgml/Styles {} styles)))

(defn- nodes-by-vertex-label [g label]
  (->> (lg/nodes g)
       (filter (fn [node-id]
                 (= label (:labelV (la/attrs g node-id)))))))

(defn- graph->dep-dgml [g]
  (let [method-ids (nodes-by-vertex-label g "METHOD")
        call-ids (nodes-by-vertex-label g "CALL")
        methods (->> method-ids
                     (map #(la/attrs g %)))
        edges (->> call-ids
                   (map
                    (fn [call-id]
                      (let [incoming-methods (->> (lg/predecessors g call-id)
                                                  (map #(la/attrs g %))
                                                  (filter (comp #{"METHOD"} :labelV))
                                                  (map :id))
                            outgoing-methods (->> (lg/successors g call-id)
                                                  (filter (fn [target-id]
                                                            (= "CALL" (la/attr g [call-id target-id] :labelE)))))]
                                                  ; (map #(la/attrs g %))
                                                  ; (filter (comp #{"METHOD"} :labelV))
                                                  ; (map :id))]
                        (assert (= (count incoming-methods) 1) (pr-str incoming-methods))
                        (assert (= (count outgoing-methods) 1) (pr-str {:call-id call-id
                                                                        :call (la/attrs g call-id)
                                                                        :outgoing-methods outgoing-methods}))
                        [(first incoming-methods) (first outgoing-methods)]))))]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {}
                              (concat
                               (for [method methods]
                                 (xml/element ::dgml/Node
                                              {:Id (:id method)
                                               :Label (:node__METHOD__NAME method)
                                               :Name (:node__METHOD__FULL_NAME method)
                                               :Category "Function"}))
                               (for [filename (->> methods
                                                   (map :node__METHOD__FILENAME)
                                                   (distinct))]
                                 (xml/element ::dgml/Node
                                              {:Id filename
                                               :Label filename
                                               :Category "Namespace"
                                               :Group "Expanded"}))))
                 (xml/element ::dgml/Links {}
                              (concat
                               (for [[source target] edges]
                                 (xml/element ::dgml/Link {:Source source :Target target}))
                               (for [method methods]
                                 (let [source (:node__METHOD__FILENAME method)
                                       target (:id method)]
                                   (xml/element ::dgml/Link {:Source source :Target target :Category "Contains"})))))
                 (xml/element ::dgml/Styles {} style/styles))))

(defn- ->graph [data]
  (assert (= ::graphml/graphml (:tag data)))
  (let [graph (->> (:content data)
                   (filter (comp #{::graphml/graph} :tag))
                   (first))
        nodes (->> (:content graph)
                   (filter (comp #{::graphml/node} :tag)))
        edges (->> (:content graph)
                   (filter (comp #{::graphml/edge} :tag)))
        g (lg/digraph)
        g (reduce (fn [g node-el]
                    (let [node-id (:id (:attrs node-el))]
                      (-> g
                          (lg/add-nodes node-id)
                          (add-attrs node-id (:attrs node-el))
                          (add-attrs node-id (parse-data-elements node-el)))))
                  g nodes)
        g (reduce (fn [g edge-el]
                    (let [{:keys [source target]} (:attrs edge-el)
                          edge [source target]]
                      (-> g
                          (lg/add-edges edge)
                          (add-attrs edge (:attrs edge-el))
                          (add-attrs edge (parse-data-elements edge-el)))))
                  g edges)]
    g))

(defn extract [{:keys [input-file output-file]}]
  (let [data (xml/parse-str (slurp input-file))
        g (->graph data)
        dgml (graph->dep-dgml g)]
    (sdgml/write-to-file output-file dgml)))

(comment
  (doseq [lang ["go" #_"js" #_"py" "rb" "ts"]]
    (let [input-file (str "test/resources/joern-cpg/out-" lang "/export.xml")
          data (xml/parse-str (slurp input-file))
          g (->graph data)
          sg (lg/subgraph g (concat
                             (nodes-by-vertex-label g "FILE")
                             (nodes-by-vertex-label g "METHOD")
                             (nodes-by-vertex-label g "CALL")))
          output-path "../../../../shared"]
      (->> (graph->all-dgml g)
           (sdgml/write-to-file (str output-path "/joern-" lang ".dgml")))
      (->> (graph->all-dgml sg)
           (sdgml/write-to-file (str output-path "/joern-sub-" lang ".dgml")))
      (->> (graph->dep-dgml g)
           (sdgml/write-to-file (str output-path "/joern-dep-" lang ".dgml")))))

  (def input-file "test/resources/joern-cpg/out-ts/export.xml")
  (def input-file "test/resources/joern-cpg/out-go/export.xml")

  (def data (xml/parse-str (slurp input-file)))

  (def g (->graph data))

  (graph->all-dgml g))
