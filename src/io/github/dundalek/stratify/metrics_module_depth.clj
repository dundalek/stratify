(ns io.github.dundalek.stratify.metrics-module-depth
  (:require
   [clojure.java.io :as io]
   [io.github.dundalek.stratify.kondo :as kondo]))

(defn count-file-lines [filename]
  (with-open [rdr (io/reader filename)]
    (count (line-seq rdr))))

(defn calculate-depth [analysis]
  (let [vars-by-ns (->> analysis
                        :var-definitions
                        (group-by :ns))]
    (->> analysis
         :namespace-definitions
         (map (fn [{:keys [name filename]}]
                (let [loc (count-file-lines filename)
                      var-count (count (vars-by-ns name))
                      private-var-count (count (filter :private (vars-by-ns name)))
                      public-var-count (count (remove :private (vars-by-ns name)))
                      var-loc (->> (vars-by-ns name)
                                   (map (fn [{:keys [row end-row]}]
                                          (inc (- end-row row))))
                                   (reduce +))]
                  (assert (pos? public-var-count))
                  [(str name)
                   {:loc loc
                    :var-loc var-loc
                    :var-count var-count
                    :private-var-count private-var-count
                    :public-var-count public-var-count
                    :module-depth (/ #_loc var-loc public-var-count)}])))
         (into {}))))

(comment
  (def analysis (kondo/analysis ["src"]))

  (calculate-depth analysis))
