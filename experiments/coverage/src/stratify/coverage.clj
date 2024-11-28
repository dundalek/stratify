(ns stratify.coverage
  (:require
   [clojure.string :as str]
   [io.github.dundalek.stratify.internal :as internal]
   [jsonista.core :as json]))

(defn coverage-line-summary [lines]
  (let [total (count lines)
        instrumented (->> lines (remove nil?) count)
        covered (->> lines (filter #(and (number? %) (pos? %))) count)
        uncovered (->> lines (filter #(and (number? %) (zero? %))) count)
        partially (->> lines (filter true?) count)]
    (assert (= instrumented (+ covered uncovered partially)))
    {:total total
     :instrumented instrumented
     :covered covered
     :uncovered uncovered
     :partially partially
     :ratio (when (pos? instrumented)
              (double (/ (+ covered partially)
                         instrumented)))}))

(comment
  (def coverage (-> (slurp "../../target/coverage/codecov.json")
                    (json/read-value json/default-object-mapper)
                    (get "coverage")))

  (let [filename "io/github/dundalek/stratify/metrics.clj"
        lines (rest (get coverage filename))] ; rest to skip line 0
    (coverage-line-summary lines))

  (def prefix "../../src")
  (def result (internal/run-kondo [prefix]))

  (->> result
       :analysis
       :namespace-definitions
      ; (take 1)
       (map (fn [{:keys [filename]}]
              (let [filename (str/replace filename (str prefix "/") "")]
                [filename (coverage-line-summary (rest (get coverage filename)))])))
       (sort-by first))

  (->> result
       :analysis
       :var-definitions
      ; (take 1)
       (map (fn [{:keys [name filename row end-row]}]
              (let [filename (str/replace filename (str prefix "/") "")
                    file-lines (get coverage filename)]
                [filename name (coverage-line-summary (subvec file-lines row (inc end-row)))])))
       (sort-by first)))
