(ns io.github.dundalek.stratify.metrics-lakos
  (:require
   [loom.alg-generic :as algg]
   [loom.graph :as lg]))

(defn count-transitive-dependencies [g node]
  (count (algg/post-traverse #(lg/successors g %) node)))

(defn cumulative-component-dependency [g]
  (->> (lg/nodes g)
       (map (fn [node] (count-transitive-dependencies g node)))
       (reduce +)))

(defn average-component-dependency [g]
  (double
   (/ (cumulative-component-dependency g)
      (count (lg/nodes g)))))

(defn relative-average-component-dependency [g]
  (/ (average-component-dependency g)
     (count (lg/nodes g))))

(defn cumulative-component-dependency-of-binary-tree [tree-size]
  (loop [current-node 1
         level 1
         max-nodes-up-to-current-level 1
         ccd-of-binary-tree 0]
    (if (> current-node tree-size)
      ccd-of-binary-tree
      (let [[next-level next-max-nodes-up-to-current-level]
            (if (> current-node max-nodes-up-to-current-level)
              [(inc level) (+ max-nodes-up-to-current-level (Math/pow 2 level))]
              [level max-nodes-up-to-current-level])]
        (recur (inc current-node)
               next-level
               next-max-nodes-up-to-current-level
               (+ ccd-of-binary-tree next-level))))))

(defn normalized-cumulative-component-dependency [g]
  (double
   (/ (cumulative-component-dependency g)
      (cumulative-component-dependency-of-binary-tree (count (lg/nodes g))))))
