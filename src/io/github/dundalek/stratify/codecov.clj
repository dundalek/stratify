(ns io.github.dundalek.stratify.codecov
  (:require
   [clojure.java.io :as io]
   [jsonista.core :as json]))

(defn load-coverage [filename]
  (-> (json/read-value (io/file filename) json/default-object-mapper)
      (get "coverage")
      ;; codecov uses 1-based indexing
      ;; remove the first padded values to make it 0-based indexed
      (update-vals #(subvec % 1))))

(defn- coverage-line-summary [lines coverage-only?]
  (let [total (count lines)
        instrumented (->> lines (remove nil?) count)
        covered (->> lines (filter #(and (number? %) (pos? %))) count)
        uncovered (->> lines (filter #(and (number? %) (zero? %))) count)
        partially (->> lines (filter true?) count)
        coverage (when (pos? instrumented)
                   (double (/ (+ covered partially) instrumented)))]
    (assert (= instrumented (+ covered uncovered partially)))
    (if coverage-only?
      coverage
      {:total total
       :instrumented instrumented
       :covered covered
       :uncovered uncovered
       :partially partially
       :coverage coverage})))

(defn line-coverage [lines]
  (coverage-line-summary lines true))

(defn make-line-coverage-lookup [filename]
  (let [coverage (load-coverage filename)]
    (fn [filename]
      (some-> (get coverage filename) line-coverage))))

(comment
  (def lookup (make-line-coverage-lookup "target/coverage/codecov.json"))
  (lookup "io/github/dundalek/stratify/metrics.clj")

  (def coverage (load-coverage "target/coverage/codecov.json"))
  (let [filename "io/github/dundalek/stratify/metrics.clj"
        lines (get coverage filename)]
    (line-coverage lines)))
