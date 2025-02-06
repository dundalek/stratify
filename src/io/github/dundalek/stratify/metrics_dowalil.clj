(ns io.github.dundalek.stratify.metrics-dowalil
  (:require
   [io.github.dundalek.stratify.kondo :as kondo]))

;; https://www.archunit.org/userguide/html/000_Index.html#_visibility_metrics_by_herbert_dowalil

(defn relative-visibilities [analysis]
  (let [vars-by-ns (->> analysis
                        :var-definitions
                        (group-by :ns))]
    (->> analysis
         :namespace-definitions
         (map (fn [{:keys [name]}]
                (let [num-elements (count (vars-by-ns name))
                      num-visible-elements (count (->> (vars-by-ns name)
                                                       (remove :private)))]
                  {:name name
                   :num-elements num-elements
                   :num-visible-elements num-visible-elements
                   :relative-visibility (/ num-visible-elements num-elements)}))))))

(defn average-relative-visibility [visibilities]
  (/ (reduce + (map :relative-visibility visibilities))
     (count visibilities)))

(defn global-relative-visibility [visibilities]
  (/ (reduce + (map :num-visible-elements visibilities))
     (reduce + (map :num-elements visibilities))))

(comment
  (def analysis (:analysis (kondo/run-kondo ["src"])))

  (relative-visibilities analysis)
  (double (global-relative-visibility (relative-visibilities analysis)))
  (double (average-relative-visibility (relative-visibilities analysis))))
