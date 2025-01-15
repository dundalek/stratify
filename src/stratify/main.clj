(ns stratify.main
  (:require
   [babashka.cli :as cli]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.main :as clj-main]
   [clojure.repl.deps :as deps]
   [clojure.string :as str]
   [io.github.dundalek.stratify.codecharta :as-alias codecharta]
   [io.github.dundalek.stratify.graphviz :as-alias graphviz]
   [io.github.dundalek.stratify.internal :as stratify]
   [io.github.dundalek.stratify.metrics :as-alias metrics]
   [io.github.dundalek.stratify.overarch :as-alias overarch]
   [io.github.dundalek.stratify.pulumi :as-alias pulumi]
   [io.github.dundalek.stratify.report :as-alias report]
   [malli.error :as me]))

(def ^:private source-formats #{"clj" "dot" "overarch" "pulumi"})

(def ^:private target-formats #{"codecharta" "dgml"})

(defn- format-choice-list [choices]
  (->> choices
       sort
       (map #(str "\"" % "\""))
       (str/join ", ")))

(def ^:private cli-spec
  {:out {:alias :o
         :ref "<file>"
         :desc "Output file, default \"-\" standard output"
         :default "-"}
   :from {:alias :f
          :ref "<format>"
          :desc (str "Source format, choices: " (format-choice-list source-formats))
          :validate source-formats
          :default "clj"}
   :to {:alias :t
        :ref "<format>"
        :desc (str "Target format, choices: " (format-choice-list target-formats))
        :validate target-formats
        :default "dgml"}
   :coverage-file {:desc "Include line coverage metric from given Codecov file"
                   :ref "<file>"}
   :flat-namespaces {:coerce :boolean
                     :desc "Render flat namespaces instead of a nested hierarchy"}
   :include-dependencies {:coerce :boolean
                          :desc "Include links to library dependencies"}
   :insert-namespace-node {:desc "Group vars mixed among namespaces under a node with a given label"
                           :ref "<label>"}
   :metrics {:coerce :boolean
             :desc "Calculate and serve namespace metrics report"}
   :help {:alias :h
          :desc "Print this help message and exit"}})

(defn- print-help []
  (println "Extract DGML graph from source code")
  (println)
  (println "Usage: stratify <options> <src-paths>")
  (println)
  (println "Options:")
  (println (cli/format-opts {:spec cli-spec})))

(defn- ensure-dynamic-context-classloader! []
  (let [cl (.getContextClassLoader (Thread/currentThread))]
    (when-not (instance? clojure.lang.DynamicClassLoader cl)
      (.setContextClassLoader (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl)))))

(defn- add-deps [feature]
  (let [deps (-> (io/resource (str "io/github/dundalek/stratify/optional-deps/" feature "/deps.edn"))
                 slurp
                 (edn/read-string)
                 :deps)]
    (binding [*repl* true]
      (ensure-dynamic-context-classloader!)
      (deps/add-libs deps))))

(defn main* [& args]
  (let [parsed (cli/parse-args args {:spec cli-spec})
        {:keys [opts args]} parsed]
    (if (or (:help opts) (:h opts) (empty? args))
      (print-help)
      (let [{:keys [out metrics from to coverage-file]} opts
            output-file (if (= out "-") *out* out)]
        (cond
          (= to "codecharta")
          (do
            (add-deps "metrics")
            ((requiring-resolve `codecharta/extract)
             {:repo-path "."
              :source-paths args
              :output-prefix output-file
              :coverage-file coverage-file}))

          metrics
          (do
            (add-deps "metrics")
            (add-deps "report")
            ((requiring-resolve `report/report!)
             {:source-paths args
              :output-path (when (not= out "-") out)}))

          (= from "overarch")
          (do
            (add-deps "overarch")
            ((requiring-resolve `overarch/extract)
             {:source-paths args
              :output-file output-file}))

          (= from "dot")
          (do
            (add-deps "graphviz")
            ((requiring-resolve `graphviz/extract)
             {:input-file (first args)
              :output-file output-file
              :flat-namespaces (:flat-namespaces opts)}))

          (= from "pulumi")
          ((requiring-resolve `pulumi/extract)
           {:input-file (first args)
            :output-file output-file})

          (= from "clj")
          (stratify/extract (merge opts {:source-paths args
                                         :output-file output-file}))

          :else
          (print-help))))))

(defn clj-main-report-error [t]
  (clj-main/report-error t :target (System/getProperty "clojure.main.report" "file")))

(defn- print-full-error-report [e]
  ;; Reusing the full report file generation, but stripping other error messages so we can print more human-friendly ones.
  (let [message (with-out-str
                  (binding [*err* *out*]
                    (clj-main-report-error e)))
        stripped-message (->> (str/split message #"\n")
                              (take-last 2)
                              (str/join "\n"))]
    (if (str/starts-with? stripped-message "Full report at:")
      (println stripped-message)
      (println message))))

(defn- handle-validation-error [e]
  (binding [*out* *err*]
    (print "Invalid input: ")
    (prn (-> e ex-data :data :explain me/humanize))
    (println)
    (print-full-error-report e)))

(defn report-error [t]
  (cond
    (= (:type (ex-data t)) :malli.core/coercion)
    (handle-validation-error t)

    (keyword? (:code (ex-data t)))
    (do
      (binding [*out* *err*]
        (println "Error:")
        (println (ex-message t))
        (println)
        (println "Code:")
        (println (:code (ex-data t)))
        (println)
        (println "Caused by:"))
      (clj-main-report-error t))

    :else
    (do
      (binding [*out* *err*]
        (println "Unknown error")
        (println "Please report an issue with details at https://github.com/dundalek/stratify/issues")
        (println)
        (println "Caused by:"))
      (clj-main-report-error t))))

(defn -main [& args]
  (try
    (apply main* args)
    (catch Throwable t
      (report-error t)
      (System/exit 1))))

(comment
  (main* "--help")

  (main* "src")

  (main* "NON_EXISTING")
  (report-error *e)

  (main* "--out" "out.dgml" "src")
  (main* "--flat-namespaces" "src")

  (main* "-f" "overarch" "-o" "banking.dgml" "target/projects/overarch/models/banking")

  (main* "-f" "bla")

  (cli/parse-args [] {:spec cli-spec}))
