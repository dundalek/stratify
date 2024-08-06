(ns io.github.dundalek.stratify.metrics-allen)

(defn system-size [system]
  (:num-nodes system))

(defn system-edges [system]
  (:edges system))

(defn log2 [x]
  (/ (Math/log x) (Math/log 2)))

(defn incidence-matrix [num-nodes edges]
  (mapv (fn [node]
          (mapv (fn [[s t]]
                  (if (or (= s node) (= t node)) 1 0))
                edges))
        (range (inc num-nodes))))

(defn ->pl [num-nodes edges]
  (let [incidence (incidence-matrix num-nodes edges)
        pl (-> (frequencies incidence)
               (update-vals #(/ % (inc num-nodes))))]
    (->> (for [[node row] (map-indexed list incidence)]
           [node (get pl row)])
         (into {}))))

(defn ->pli [num-nodes edges i]
  (->pl num-nodes
        (->> edges
             (filter (fn [[u v]] (or (= u i) (= v i)))))))

(defn information
  "Aka minimum description length I(S)"
  [num-nodes pl-index]
  (->> (for [i (range (inc num-nodes))]
         (- (log2 (get pl-index i))))
       (reduce +)))

(defn system-information [system]
  (information (system-size system) (->pl (system-size system) (system-edges system))))

(defn node-information [num-nodes edges i]
  (information num-nodes (->pli num-nodes edges i)))

(defn coupling [system]
  (let [num-nodes (system-size system)
        edges (system-edges system)
        pl (->pl num-nodes edges)]
    (- (->> (for [i (range (inc num-nodes))]
              (node-information num-nodes edges i))
            (reduce +))
       (information num-nodes pl))))

(defn entropy
  "Aka H(S)"
  [num-nodes pl-index]
  (->> (for [i (range (inc num-nodes))]
         (* (/ 1 (inc num-nodes))
            (- (log2 (get pl-index i)))))
       (reduce +)))

(defn system-entropy [system]
  (entropy (system-size system) (->pl (system-size system) (system-edges system))))

(defn node-entropy [num-nodes edges i]
  (entropy num-nodes (->pli num-nodes edges i)))

(defn excess-entropy
  "Aka C(S)"
  [system]
  (let [num-nodes (system-size system)
        edges (system-edges system)]
    (- (->> (for [i (range (inc num-nodes))]
              (node-entropy num-nodes edges i))
            (reduce +))
       (entropy num-nodes (->pl num-nodes edges)))))

;; Proof in [4] E. B. Allen, T. M. Khoshgoftaar, and Y. Chen. Properties of cohesion of graph abstractions of software. Technical Report TR-CSE-99-5, Florida Atlantic University, Boca Raton, FL USA, Apr. 1999.
(defn complete-graph-excess-entropy [n]
  (assert (< 2 n))
  (* (dec n) (log2 (inc n))))

(defn cohesion [system]
  (/ (excess-entropy system)
     (complete-graph-excess-entropy (system-size system))))
