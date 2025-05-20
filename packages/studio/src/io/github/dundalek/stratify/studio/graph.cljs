(ns io.github.dundalek.stratify.studio.graph
  (:require
   [clojure.string :as str]
   [loom.attr :as la]
   [loom.graph :as lg]
   [medley.core :as medley]
   [loom.alg-generic :as alge]))

;; Representations
;; loom - loom graph instance
;; graph - nodes, edges in clj
;; js - nodes, edges with nested data field (can be passed to cytoscape constructor as elements)
;; cy - cytoscape instance representation

;; graph seems to be redundant, only due to experiments for historical reasons

(defn node-ids [graph]
  (->> graph
       :nodes
       (map :id)
       set))

(defn edge-pairs [graph]
  (->> graph
       :edges
       (map (juxt :source :target))
       (into #{})))

#_(defn collapse [g expanded]
    (assert (set? expanded))
    (let [visible-edges (->> expanded
                             (mapcat (fn [source]
                                       (->> (lg/successors g source)
                                            (map (fn [target]
                                                   [source target]))))))]
      (-> (lg/digraph)
          (lg/add-edges* visible-edges))))

(defn subgraph [g nodes]
  (let [node? (set nodes)
        new-edges (->> (lg/edges g)
                       (filter (fn [[source target]]
                                 (and (node? source)
                                      (node? target)))))]
    (-> (lg/digraph)
        (lg/add-nodes* (->> (lg/nodes g)
                            (filter node?)))
        (lg/add-edges* new-edges))))

(defn collapse [g expanded]
  (assert (set? expanded))
  (let [roots (->> (lg/nodes g)
                   (filter #(zero? (lg/in-degree g %))))
        visible (loop [[x & xs] roots
                       visible (set roots)]
                  (if (some? x)
                    (if (contains? expanded x)
                      ;; add descendants to visible
                      (recur (reduce conj xs (lg/successors g x))
                             (reduce conj visible (lg/successors g x)))
                      (recur xs visible))
                    visible))]
    (subgraph g visible)))
    ;     visible-edges (->> expanded
    ;                        (mapcat (fn [source]
    ;                                  (->> (lg/successors g source)
    ;                                       (map (fn [target]
    ;                                              [source target]))))))]
    ; (-> (lg/digraph)
    ;     (lg/add-edges* visible-edges))))

(defn ->loom [{:keys [nodes edges]}]
  (reduce
   (fn [g {:keys [id parent label]}]
     (cond-> g
       (some? parent) (la/add-attr id :parent parent)
       (some? label) (la/add-attr id :label label)))
   (-> (lg/digraph)
       (lg/add-nodes* (->> nodes (map :id)))
       (lg/add-edges* (->> edges (map (juxt :source :target)))))
   nodes))

(defn ->graph [g]
  {:nodes (->> (lg/nodes g)
               (map (fn [node-id]
                      (medley/assoc-some {:id node-id}
                                         :parent (la/attr g node-id :parent)
                                         :label (la/attr g node-id :label))

                         ;; TODO preserver attributes
                      #_(assoc (la/attrs g node-id)
                               :id node-id))))
   :edges (->> (lg/edges g)
               (map (fn [[source target]]
                      {:source source
                       :target target})))})

(comment
  (subgraph (->loom samples/simple) ["a" #_"b"])

  (let [g (->loom samples/simple)
        roots (->> (lg/nodes g)
                   (filter #(zero? (lg/in-degree g %))))]
    ; (lg/subgraph g roots))
    (lg/subgraph g ["a" "d"])))

(defn js->loom [^js graph]
  (let [g (-> (lg/digraph)
              (lg/add-nodes* (->> (.-nodes graph) (map #(.-data.id ^js %))))
              (lg/add-edges* (->> (.-edges graph) (map (juxt #(.-data.source ^js %)
                                                             #(.-data.target ^js %))))))]
    (reduce
     (fn [g ^js node]
       (let [node-id (.-data.id node)
             x (.-x node)
             y (.-y node)
             parent (.-data.parent node)]
         (cond-> g
           x (la/add-attr node-id :x x)
           y (la/add-attr node-id :y y)
           parent (la/add-attr node-id :parent parent))))
     g (.-nodes graph))))

(defn loom->js [g]
  #js {:nodes (->> (lg/nodes g)
                   (map (fn [node-id]
                          (let [style (medley/assoc-some nil
                                                         :width (la/attr g node-id :width)
                                                         :height (la/attr g node-id :height))]
                            (-> (medley/assoc-some {:data (medley/assoc-some {:id node-id}
                                                                             :parent (la/attr g node-id :parent)
                                                                             :label (la/attr g node-id :label))}
                                                   :style style
                                                   :x (la/attr g node-id :x)
                                                   :y (la/attr g node-id :y))
                                (clj->js)))))
                   (to-array))
       :edges (->> (lg/edges g)
                   (map (fn [[source target]]
                          #js {:data #js {:source source
                                          :target target}}))
                   (to-array))})

(defn px-value->num [x]
  (js/console.log ">>" x)
  (assert (string? x))
  (->> (str/replace x #"px$" "")
       parse-long))

(defn cy->loom [^js cy]
  (let [g (-> (lg/digraph)
              (lg/add-nodes* (->> (.nodes cy) (map #(.id %))))
              (lg/add-edges* (->> (.edges cy) (map (juxt #(-> % .source .id)
                                                         #(-> % .target .id))))))]
    (reduce
     (fn [g node]
       (let [position (.position node)
             parent-id (-> node .parent .id)
             label (.data node "label")
             width (some-> (.style node "width")
                           px-value->num)
             height (some-> (.style node "height")
                            px-value->num)]
         (cond-> g
           position (la/add-attr (.id node) :x (.-x position))
           position (la/add-attr (.id node) :y (.-y position))
           parent-id (la/add-attr (.id node) :parent parent-id)
           label (la/add-attr (.id node) :label label)
           width (la/add-attr (.id node) :width width)
           height (la/add-attr (.id node) :height height))))

     g (.nodes cy))))

(defn null-layout [g]
  (reduce (fn [g node-id]
            (-> g
                (la/add-attr node-id :x 0)
                (la/add-attr node-id :y 0)))
          g
          (lg/nodes g)))

(defn dimensions [g]
  (reduce
   (fn [[width height] node-id]
     [(max width (la/attr g node-id :x))
      (max height (la/attr g node-id :y))])
   [0 0]
   (lg/nodes g)))

(defn namespace-prefixes [namespace-str]
  (let [segments (str/split namespace-str #"\.")]
    (for [i (range 1 (count segments))]
      (->> (take i segments)
           (str/join ".")))))

(defn- add-node-namespace-parents [g node-id]
  (loop [g g
         node-id node-id]
    (let [segments (str/split node-id #"\.")
          parent-id (->> segments butlast (str/join "."))]
      (if (str/blank? parent-id)
        g
        (recur (-> g
                   (lg/add-nodes parent-id)
                   (la/add-attr node-id :parent parent-id))
               parent-id)))))

(defn add-namespace-hierarchy-parents [g]
  (reduce add-node-namespace-parents
          g
          (lg/nodes g)))

(defn- add-clustered-namespace-node [g node-id]
  (loop [g g
         node-id node-id
         ;; slash separator for experimenting with graphing vars inside namespaces,
         ;; but will need additional handling to preserver name when joining segments
         segments (str/split node-id #"\.|/")]
    (if (empty? segments)
      g
      (let [cluster-label (str/join "." segments)
            cluster-id cluster-label #_(str "cluster_" cluster-label)
            attrs {:label cluster-label}]
        (recur (-> g
                   (lg/add-nodes cluster-id)
                   (assoc-in [:attrs node-id :parent] cluster-id)
                   (assoc-in [:attrs cluster-id] attrs))
               cluster-id
               (butlast segments))))))

;; avoiding potential id conflicts when adding cluster nodes:
;; - consider adding also a prefix to the node
;; - or autogenerate the cluster id
(defn add-clustered-namespace-hierarchy [g]
  (reduce add-clustered-namespace-node
          g
          (lg/nodes g)))

(defn extract-parents-graph [g]
  (let [parent-connections (->> (lg/nodes g)
                                (keep (fn [node-id]
                                        (when-some [parent (la/attr g node-id :parent)]
                                          [parent node-id]))))
        parent-graph (-> (lg/digraph)
                         (lg/add-nodes* (lg/nodes g))
                         (lg/add-edges* parent-connections))]
                         ; (assoc :attrs (:attrs g)))]
    parent-graph))

(defn count-descendents [g]
  ;; TODO optimize for linear pass
  (letfn [(count-descendents-r [node-id]
            (if-some [successors (seq (lg/successors g node-id))]
              (reduce (fn [acc child-id]
                        (+ acc (count-descendents-r child-id)))
                      0
                      successors)
              1))]
    (reduce (fn [m node-id]
              (let [cnt (count-descendents-r node-id)]
                (cond-> m
                  (pos? cnt) (assoc node-id cnt))))
            {}
            (lg/nodes g))))

(defn aggregate-collapsed-next [graph visible]
  (assert (set? visible))
  ;; naive, can be optimized
  (letfn [(find-aggregate-parent [node-id]
            (let [parent-id (la/attr graph node-id :parent)]
              (if (nil? parent-id)
                node-id
                (let [aggregate-id (find-aggregate-parent parent-id)]

                  (if (and (= aggregate-id parent-id)
                           (visible aggregate-id))
                    node-id
                    aggregate-id)))))]
    (let [node->target (->> (lg/nodes graph)
                            (map (fn [node-id]
                                   [node-id (find-aggregate-parent node-id)]))
                            (into {}))
          nodes (->> (lg/nodes graph)
                     (map node->target)
                     (into #{}))
          aggregated-edges (->> (lg/edges graph)
                                (keep (fn [[source target]]
                                        (let [source-parent (node->target source)
                                              target-parent (node->target target)]
                                          (when (not= source-parent target-parent)
                                            [source-parent target-parent])))))]
      (-> (lg/digraph)
          (lg/add-nodes* nodes)
          (lg/add-edges* aggregated-edges)
          (assoc :attrs (select-keys (:attrs graph) nodes))))))

(defn parent-node-set [g]
  (->> (lg/nodes g)
       (keep (fn [node-id]
               (la/attr g node-id :parent)))
       (into #{}))
  #_(-> graph
        (graph/extract-parents-graph)
        (graph/collapse expanded)
        (lg/nodes)
        set))

(defn some-collapsed-parent-node-set [g]
  ;; for now get roots that have no parent, later might add better heuristic
  (->> (lg/nodes g)
       (remove (fn [node-id]
                 (la/attr g node-id :parent)))
       (into #{})))

(defn root-node-set [g]
  (->> (lg/nodes g)
       (filter #(zero? (lg/in-degree g %)))
       (into #{})))

(defn butterfly-hierarchy [g selected-id]
  (let [predecessors (lg/predecessors g selected-id)
        successors (lg/successors g selected-id)]
    (js/console.log ">>" selected-id predecessors successors)
    (subgraph g (concat
                 [selected-id]
                 predecessors
                 successors))))
