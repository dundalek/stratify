(ns io.github.dundalek.stratify.codecov-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.codecov :as codecov]
   [io.github.dundalek.stratify.internal :as internal]))

;; Re-generate by running `cd test/resources/coverage; clojure -M:coverage`.
(def sample-coverage-file
  "test/resources/coverage/target/coverage/codecov.json")

(deftest line-coverage
  (is (= {;; == namespaces

          ;; parent "synthetic" namespace
          ;; No calculated value for now, but in the future could perhaps aggregate coverage from children namespaces.
          "example" nil
          "example.covered" 100.0
          "example.partial" 81.82
          ;; Even though no actual code is covered, ns forms and function names count as covered.
          ;; Therefore there is likely no way for clojure to report exactly 0 coverage.
          "example.uncovered" 66.67

          ;; == functions

          "example.covered/covered" 100.0
          "example.partial/covered" 100.0

          ;; partially covered functions
          "example.partial/part" 75.0
          ;; counting lines with partially covered forms as covered (matching cloverage html report behavior)
          ;; Perhaps consider a different coefficient like 0.5 which would avoid summing up to 100.
          "example.partial/yellow" 100.0

          ;; uncovered functions - function name counts as covered, so the coverage is non-zero
          "example.partial/uncovered" 50.0
          "example.uncovered/uncovered" 50.0}

         (-> (:g (internal/analysis->graph
                  {:analysis (:analysis (internal/run-kondo ["test/resources/coverage/src"]))
                   :line-coverage (codecov/make-line-coverage-lookup
                                   {:coverage-file sample-coverage-file
                                    :strip-prefixes ["test/resources/coverage/src"]})}))
             :attrs
             (update-vals #(some-> % :line-coverage (* 10000.0) (Math/round) (/ 100.0)))))))

(deftest coverage-summary
  (is (= {:total 15
          :instrumented 11
          :covered 8
          :partially 1
          :uncovered 2
          :coverage 0.8181818181818182}
         (-> (codecov/load-coverage sample-coverage-file)
             (get  "example/partial.clj")
             (#'codecov/coverage-summary false)))))
