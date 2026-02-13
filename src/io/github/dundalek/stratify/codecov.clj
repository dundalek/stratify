(ns io.github.dundalek.stratify.codecov
  (:require
   [babashka.json :as json]
   [clojure.string :as str]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt])
  (:import
   [java.util.regex Pattern]))

(def Codecov
  [:map
   ["coverage" [:map-of :string
                [:sequential [:or nat-int? true? nil?]]]]])

(def ^:private strict-json-transformer
  (mt/transformer
   mt/strip-extra-keys-transformer
   mt/json-transformer))

(def ^:private coerce-codecov
  (m/coercer Codecov strict-json-transformer))

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
  (let [input (try
                (json/read-str (slurp filename) {:key-fn identity})
                (catch Throwable t
                  (throw (ex-info "Failed to parse Codecov file." {:code ::failed-to-parse} t))))
        data (try
               (coerce-codecov input)
               (catch Throwable t
                 (if (= (:type (ex-data t)) :malli.core/coercion)
                   (throw (ex-info (str "Failed to load Pulumi resources.\n\n"
                                        (-> t ex-data :data :explain me/humanize))
                                   {:code ::invalid-input} t))
                   (throw t))))]
    (-> data
        (get "coverage")
        ;; codecov uses 1-based indexing
        ;; remove the first padded values to make it 0-based indexed
        (update-vals #(subvec % 1)))))

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
                           {:code ::coverage-range-out-of-bounds
                            :filename filename :start start :end end}))))))))

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
