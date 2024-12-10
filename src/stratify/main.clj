(ns stratify.main
  (:require
   [babashka.cli :as cli]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.repl.deps :as deps]
   [clojure.string :as str]
   [io.github.dundalek.stratify.codecharta :as-alias codecharta]
   [io.github.dundalek.stratify.graphviz :as-alias graphviz]
   [io.github.dundalek.stratify.internal :as stratify]
   [io.github.dundalek.stratify.metrics :as-alias metrics]
   [io.github.dundalek.stratify.overarch :as-alias overarch]
   [io.github.dundalek.stratify.pulumi :as-alias pulumi]
   [io.github.dundalek.stratify.report :as-alias report]))

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
   :flat-namespaces {:coerce :boolean
                     :desc "Render flat namespaces instead of a nested hierarchy"}
   :include-dependencies {:coerce :boolean
                          :desc "Include links to library dependencies"}
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

(defn -main [& args]
  (let [parsed (cli/parse-args args {:spec cli-spec})
        {:keys [opts args]} parsed]
    (if (or (:help opts) (:h opts) (empty? args))
      (print-help)
      (let [{:keys [out metrics from to]} opts
            output-file (if (= out "-") *out* out)]
        (cond
          (= to "codecharta")
          (do
            (add-deps "metrics")
            (add-deps "codecharta")
            ((requiring-resolve `codecharta/extract)
             {:repo-path "."
              :source-paths args
              :output-prefix output-file}))

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
          (do
            (add-deps "pulumi")
            ((requiring-resolve `pulumi/extract)
             {:input-file (first args)
              :output-file output-file}))

          (= from "clj")
          (stratify/extract (merge opts {:source-paths args
                                         :output-file output-file}))

          :else
          (print-help))))))

(comment
  (-main "--help")

  (-main "src")
  (-main "--out" "out.dgml" "src")
  (-main "--flat-namespaces" "src")

  (-main "-f" "overarch" "-o" "banking.dgml" "target/projects/overarch/models/banking")

  (-main "-f" "bla")

  (cli/parse-args [] {:spec cli-spec}))
