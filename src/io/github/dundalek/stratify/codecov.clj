(ns io.github.dundalek.stratify.codecov
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [jsonista.core :as json])
  (:import
   [java.util.regex Pattern]))

(defn- coverage-summary [lines coverage-only?]
  (let [instrumented (->> lines (remove nil?) count)
        covered (->> lines (filter #(and (number? %) (pos? %))) count)
        uncovered (->> lines (filter #(and (number? %) (zero? %))) count)
        partially (->> lines (filter true?) count)
        coverage (when (pos? instrumented)
                   (/ (+ covered partially) instrumented))]
    (assert (= instrumented (+ covered uncovered partially)))
    (if coverage-only?
      coverage
      {:total (count lines)
       :instrumented instrumented
       :covered covered
       :uncovered uncovered
       :partially partially
       :coverage coverage})))

(defn- line-coverage [lines]
  (coverage-summary lines true))

(defn load-coverage [filename]
  (-> (json/read-value (io/file filename) json/default-object-mapper)
      (get "coverage")
      ;; codecov uses 1-based indexing
      ;; remove the first padded values to make it 0-based indexed
      (update-vals #(subvec % 1))))

(defn make-line-coverage-raw-lookup [{:keys [coverage-file strip-prefixes]}]
  (let [coverage (load-coverage coverage-file)
        transform-filename (if strip-prefixes
                             (let [prefix-pattern (re-pattern (str "^" (str/join "|" (map Pattern/quote strip-prefixes)) "/"))]
                               #(str/replace-first % prefix-pattern ""))
                             identity)]
    (fn
      ([filename]
       (some-> (get coverage (transform-filename filename))
               line-coverage))
      ([filename start end]
       (try
         (some-> (get coverage (transform-filename filename))
                 (subvec start end)
                 line-coverage)
         (catch IndexOutOfBoundsException _
           (throw (ex-info (str "Coverage line range is out of bounds. "
                                "Please make sure the coverage file is up-to-date with the source code.")
                           {:filename filename :start start :end end}))))))))

(defn make-line-coverage-lookup [opts]
  (comp
   ;; Maybe warn if the coverage cannot be looked up?
   #(some-> % (* 100.0))
   (make-line-coverage-raw-lookup opts)))

(comment
  (def lookup (make-line-coverage-raw-lookup "target/coverage/codecov.json"))
  (lookup "io/github/dundalek/stratify/metrics.clj")

  (def coverage (load-coverage "target/coverage/codecov.json"))
  (let [filename "io/github/dundalek/stratify/metrics.clj"
        lines (get coverage filename)]
    (line-coverage lines)))
