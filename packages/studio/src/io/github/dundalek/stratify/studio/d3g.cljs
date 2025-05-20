(ns io.github.dundalek.stratify.studio.d3g
  (:require
   ["d3" :as d3]
   [clojure.string :as str]
   [io.github.dundalek.stratify.studio.graph :as graph]
   [loom.alg :as alg]
   [loom.attr :as la]
   [loom.graph :as lg]))

(def node-radius 15)
(def cluster-padding (* node-radius 1.5))

(def node-fill-color "#999999")
(def edge-stroke-color "#AAAAAA")
(def cluster-stroke-color "#CCCCCC")
(def cluster-fill-color "#CCCCCC44")

(defn connection-arc [source target]
  (let [{x1 :x y1 :y} source
        {x2 :x y2 :y} target
        r (* (js/Math.hypot (- x1 x2) (- y1 y2))
             1.2)
        sweep-flag 1]
    (str "M" x1 "," y1
         "A" r "," r " 0,0," sweep-flag " " x2 "," y2)))

(def initial-bounds [##Inf ##Inf ##-Inf ##-Inf])

(defn merge-bounds [g [min-x min-y max-x max-y :as bounds] node-id]
  (let [{:keys [x y]} (when (lg/has-node? g node-id)
                        (la/attrs g node-id))]
    (if (and x y)
      [(min x min-x)
       (min y min-y)
       (max x max-x)
       (max y max-y)]
      bounds)))

(defn get-bounds [g node-id]
  ;; note: naive, should memoize
  (letfn [(get-bounds-r [bounds node-id]
            (if-some [successors (seq (lg/successors g node-id))]
              (reduce get-bounds-r
                      bounds
                      successors)
              (merge-bounds g bounds node-id)))]
    (get-bounds-r initial-bounds node-id)))

(defn process-clusters-next
  ([graph full-graph]
   (process-clusters-next graph full-graph {:cluster-padding cluster-padding}))
  ([graph full-graph {:keys [cluster-padding]}]
   (let [parent-graph (-> (graph/extract-parents-graph graph)
                          (assoc :attrs (:attrs graph)))
         parent-groups (->> (lg/nodes full-graph)
                            (group-by #(la/attr full-graph % :parent)))
         visible-parent-groups (->> (lg/nodes graph)
                                    (group-by #(la/attr graph % :parent)))
         full-parent-graph (graph/extract-parents-graph full-graph)
         full-graph-descendent-counts (graph/count-descendents full-parent-graph)
         clusters (->> (alg/topsort full-parent-graph)
                       (map (fn [cluster-id]
                              (let [label (la/attr full-graph cluster-id :label)]
                                {:id cluster-id
                                 :label label})))
                       (remove (fn [{:keys [id label]}]
                                 (or (not (lg/has-node? graph id))
                                     (zero? (count (parent-groups id)))
                                     (and (= 1 (count (parent-groups id)))
                                          (= (first (parent-groups id))
                                             label)))))
                       (map (fn [{:keys [id label]}]
                              (let [[min-x min-y max-x max-y] (get-bounds parent-graph id)]
                                {:id id
                                 :label label
                                 :x (- min-x cluster-padding)
                                 :y (- min-y cluster-padding)
                                 :width (+ (- max-x min-x) (* cluster-padding 2))
                                 :height (+ (- max-y min-y) (* cluster-padding 2))
                                 :visible-child-count (count (visible-parent-groups id))
                                 :child-count (count (parent-groups id))
                                 :descendent-count (full-graph-descendent-counts id)}))))]
     clusters)))

(defn make-renderer [{:keys [container on-node-click on-cluster-click]}]
  (let [svg-selection (d3/select container)
        g (.append svg-selection "g")

        zoomed (fn [^js ev]
                 (-> svg-selection
                     (.select "g")
                     (.attr "transform" (.-transform ev))))

        zoom (-> (d3/zoom)
                 (.on "zoom" zoomed))
        _ (.call svg-selection
                 zoom)
        cluster-selection (-> g (.append "g"))
        edges-selection (-> g (.append "g"))
        nodes-selection (-> g (.append "g"))]

    (fn update [{:keys [graph full-graph show-clusters?]}]
      ; (println "graph" graph)
      (let [t (-> (.transition svg-selection)
                  (.duration 750))
            [width height] (graph/dimensions graph)
            width (+ width 20)
            height (+ height 20)
            ; _ (.attr svg-selection "viewBox"
            ;          (str/join " " [0 0 width height]))
            _ (-> svg-selection
                  (.transition t)
                  (.attr "viewBox"
                         (str/join " " [0 0 width height])))

            _ (.extent zoom #js [#js [0 0]
                                 #js [width height]])

            node->label (fn [node-id]
                          (or (when (lg/has-node? full-graph node-id)
                                (la/attr full-graph node-id :label))
                              node-id))
            clusters (if show-clusters?
                       (process-clusters-next graph full-graph)
                       [])
            graph (reduce (fn [g {:keys [id x y]}]
                            (-> g
                                (la/add-attr id :x x)
                                (la/add-attr id :y y)))
                          graph
                          clusters)
            cluster? (->> clusters (map :id) set)
            _cluster-elements (-> cluster-selection
                                  (.selectChildren)
                                  (.data (to-array clusters)
                                         #(:id %))
                                  (.join
                                   (fn [enter]
                                     (doto (-> enter
                                               (.append "g")
                                               (.attr "transform" (fn [{:keys [x y]}]
                                                                    (str "translate(" x ", " y ")")))
                                               (.on "click" (fn [_ev d] (on-cluster-click (:id d)))))
                                       ;; draw cluster rectangles
                                       (->
                                        (.append "rect")
                                        (.attr "width" #(:width %))
                                        (.attr "height" #(:height %))
                                        (.attr "fill" cluster-fill-color)
                                        (.attr "stroke" cluster-stroke-color)
                                        (.attr "stroke-width" 1))
                                       ;; draw cluster labels
                                       (->
                                        (.append "text")
                                        (.text (fn [{:keys [id child-count descendent-count]}]
                                                 (str (node->label id) " (" child-count "; " descendent-count ")")))
                                        (.attr "font-family" "sans-serif")
                                        (.attr "font-size" 12)
                                        (.attr "fill" node-fill-color)
                                        (.attr "y" -1))))
                                   (fn [update]
                                     (-> update
                                         (.transition t)
                                         (.attr "transform" (fn [{:keys [x y]}]
                                                              (str "translate(" x ", " y ")")))
                                         (.call (fn [x]
                                                  (-> x
                                                      (.select "text")
                                                      (.attr "transform" (fn [{:keys [visible-child-count]}]
                                                                           (if (zero? visible-child-count)
                                                                             "rotate(-20)"
                                                                             "rotate(0)")))
                                                      (.attr "fill" (fn [{:keys [visible-child-count]}]
                                                                      (if (zero? visible-child-count)
                                                                        "#000000"
                                                                        node-fill-color))))))
                                         (.select "rect")
                                         (.attr "width" #(:width %))
                                         (.attr "height" #(:height %))))))

            ;; plot edges
            _ (-> edges-selection
                  (.selectChildren)
                  (.data (to-array (lg/edges graph))
                         (fn [edge] (str edge)))
                  (.join
                   (fn [enter]
                     (-> enter
                         (.append "path")
                         (.attr "fill" "none")
                         (.attr "stroke-width" 2)
                         (.attr "stroke" edge-stroke-color)
                         (.attr "d",
                                (fn [[source target]]
                                  ; (js/console.log ">> d" source target)
                                  (connection-arc (la/attrs graph source) (la/attrs graph target)))))))
                  (.transition t)
                  (.attr "d",
                         (fn [[source target]]
                           ; (js/console.log ">> d" source target)
                           (connection-arc (la/attrs graph source) (la/attrs graph target)))))
                    ; (fn [exit]
                    ;   (js/console.log ">> exit")
                    ;   (.remove exit))))]))))

            nodes (->> (lg/nodes graph)
                       (remove #(and (cluster? %))))
                                   ; (zero? (lg/out-degree graph %))
                                   ; (zero? (lg/in-degree graph %)))))
            _ (-> nodes-selection
                  (.selectChildren)
                  (.data (to-array nodes)
                         (fn [node-id] node-id))
                  (.join
                   (fn [enter]
                     (doto (-> enter
                               (.append "g")
                               (.attr "transform" (fn [node-id]
                                                   ; (js/console.log "transform update" node-id)
                                                    (let [{:keys [x y]} (la/attrs graph node-id)]
                                                      (str "translate(" x ", " y ")"))))
                               (.on "click" (fn [_ev d] (on-node-click d))))
                                ; (.attr "transform" (fn [node-id]
                                ;
                                ;                      (js/console.log "transform enter")
                                ;                      (let [{:keys [x y]} (la/attrs graph node-id)]
                                ;                        (str "translate(" x ", " y ")")))))
                            ;; plot node circles
                       (->
                        (.append "circle")
                        ; (.attr "r" node-radius)
                        (.attr "r" (fn [node-id]
                                     (if-some [width (la/attr graph node-id :width)]
                                       (do
                                         (assert (= width (la/attr graph node-id :height)))
                                         (/ width 2))
                                       node-radius)))
                        (.attr "fill" node-fill-color)
                        (.append "title")
                        (.text node->label))
                       (->
                        (.append "text")
                        (.attr "font-family" "sans-serif")
                        (.attr "font-size" 14)
                        (.attr "x" -5)
                        (.attr "y" 3))
                            ;; plot node labels
                       (->
                        (.append "text")
                        (.attr "transform", "rotate(-20)")
                        (.attr "font-family", "sans-serif")
                        (.attr "font-size", 14)
                        (.attr "x" (fn [node-id]
                                     (or (some-> (la/attr graph node-id :width)
                                                 (/ 2))
                                         node-radius)))
                        (.attr "y" (fn [node-id]
                                     (let [height (or (la/attr graph node-id :height)
                                                      node-radius)]
                                       (- (/ height 2)))))
                        (.text node->label))))
                   (fn [update]
                     (-> update)))
                  ; (.on "click" (fn [_ev d] (on-node-click d)))
                  (.call (fn [x]
                           (-> x
                               (.transition t)
                               (.attr "transform" (fn [node-id]
                                                   ; (js/console.log "transform update" node-id)
                                                    (let [{:keys [x y]} (la/attrs graph node-id)]
                                                      (str "translate(" x ", " y ")")))))
                           #_(-> x
                                 (.select "text")
                                 (.text (fn [node-id]
                                          (let [hidden-count (- (lg/out-degree full-graph node-id)
                                                                (lg/out-degree graph node-id))]
                                            (when (pos? hidden-count)
                                              (str "+" hidden-count)))))))))]))))
