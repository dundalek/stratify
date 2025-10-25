(ns stratify.main
  (:require
   [babashka.cli :as cli]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.main :as clj-main]
   [clojure.repl.deps :as deps]
   [clojure.string :as str]
   [io.github.dundalek.stratify.codecharta :as-alias codecharta]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.gabotechs-dep-tree :as-alias dep-tree]
   [io.github.dundalek.stratify.graphviz :as-alias graphviz]
   [io.github.dundalek.stratify.internal :as stratify]
   [io.github.dundalek.stratify.lsp :as lsp]
   [io.github.dundalek.stratify.metrics :as-alias metrics]
   [io.github.dundalek.stratify.overarch :as-alias overarch]
   [io.github.dundalek.stratify.pulumi :as-alias pulumi]
   [io.github.dundalek.stratify.report :as-alias report]
   [io.github.dundalek.stratify.scip :as-alias scip]
   [io.github.dundalek.stratify.studio.main :as-alias studio]))

(def ^:private language-extractors
  #{"clj" "go-lsp" "go-scip" "lua-lsp" "python-scip" "ruby-scip" "rust-lsp" "ts-scip" "ts-lsp" "zig-lsp"})

(def ^:private other-formats
  #{"dgml" "dot" "overarch" "pulumi" "scip"})

(def ^:private source-formats
  (into language-extractors other-formats))

(def ^:private target-formats #{"codecharta" "dep-tree" "dgml"})

(defn- format-choice-list [choices]
  (->> choices
       sort
       (map #(str "\"" % "\""))
       (str/join ", ")))

(defn- format-categorized-source-formats []
  (str "Source format, choices:\n"
       "                Language extractors: " (format-choice-list language-extractors) "\n"
       "                Other formats: " (format-choice-list other-formats)))

(def ^:private cli-spec
  {:out {:alias :o
         :ref "<file>"
         :desc "Output file, default \"-\" standard output"
         :default "-"}
   :from {:alias :f
          :ref "<format>"
          :desc (format-categorized-source-formats)
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
   :metrics-delta {:coerce :boolean
                   :desc "Calculate and serve metrics delta report"}
   :studio {:coerce :boolean
            :desc "Open web-based visualizer"}
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

(defn- add-libs [deps]
  (binding [*repl* true]
    (ensure-dynamic-context-classloader!)
    (deps/add-libs deps)))

(defn- add-deps [feature]
  (let [deps (-> (io/resource (str "io/github/dundalek/stratify/optional-deps/" feature "/deps.edn"))
                 slurp
                 (edn/read-string)
                 :deps)]
    (add-libs deps)))

(defn- open-studio [g]
  ; (add-libs '{stratify/studio {:local/root "packages/studio"}})
  ((requiring-resolve `studio/open) g))

(defn main* [& args]
  (let [parsed (cli/parse-args args {:spec cli-spec})
        {:keys [opts args]} parsed]
    (if (or (:help opts) (:h opts) (empty? args))
      (print-help)
      (let [{:keys [out metrics metrics-delta from to coverage-file studio]} opts
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

          (= to "dep-tree")
          ((requiring-resolve `dep-tree/extract)
           {:source-paths args
            :output-file output-file})

          metrics
          (do
            (add-deps "metrics")
            (add-deps "report")
            ((requiring-resolve `report/report!)
             {:source-paths args
              :output-path (when (not= out "-") out)
              :notebook-path "io/github/dundalek/stratify/notebook.clj"}))

          metrics-delta
          (do
            (add-deps "metrics")
            (add-deps "metrics-delta")
            (add-deps "report")
            ((requiring-resolve `report/report!)
             {:source-paths args
              :output-path (when (not= out "-") out)
              :notebook-path "io/github/dundalek/stratify/notebook_delta.clj"}))

          (and studio (= from "go-lsp"))
          (let [g (lsp/extract-go {:root-path (first args)})]
            (open-studio g))

          (and studio (= from "lua-lsp"))
          (let [g (lsp/extract-lua {:root-path (first args)})]
            (open-studio g))

          (and studio (= from "rust-lsp"))
          (let [g (lsp/extract-rust {:root-path (first args)})]
            (open-studio g))

          (and studio (= from "ts-lsp"))
          (let [g (lsp/extract-typescript {:root-path (first args)})]
            (open-studio g))

          (and studio (= from "zig-lsp"))
          (let [g (lsp/extract-zig {:root-path (first args)})]
            (open-studio g))

          (and studio (= from "overarch"))
          (do
            (add-deps "overarch")
            (let [g ((requiring-resolve `overarch/extract-graph)
                     {:source-paths args})]
              (open-studio g)))

          (and studio (= from "dot"))
          (do
            (add-deps "graphviz")
            (let [g ((requiring-resolve `graphviz/extract-graph)
                     {:input-file (first args)
                      :output-file output-file
                      :flat-namespaces (:flat-namespaces opts)})]
              (open-studio g)))

          (and studio (= from "dgml"))
          (let [g (sdgml/load-graph (first args))]
            (open-studio g))

          (and studio (= from "scip"))
          (let [g ((requiring-resolve `scip/load-graph) {:index-file (first args)})]
            (open-studio g))

          (and studio (= from "ts-scip"))
          (let [g ((requiring-resolve `scip/load-graph-ts-scip) {:dir (first args)})]
            (open-studio g))

          (and studio (= from "go-scip"))
          (let [g ((requiring-resolve `scip/load-graph-scip-go) {:dir (first args)})]
            (open-studio g))

          (and studio (= from "python-scip"))
          (let [g ((requiring-resolve `scip/load-graph-scip-py) {:dir (first args)})]
            (open-studio g))

          (and studio (= from "ruby-scip"))
          (let [g ((requiring-resolve `scip/load-graph-scip-rb) {:dir (first args)})]
            (open-studio g))

          studio
          (let [g (stratify/extract-graph (merge opts {:source-paths args}))]
            (open-studio g))

          (= from "go-lsp")
          (let [g (lsp/extract-go {:root-path (first args)})]
            (sdgml/write-to-file output-file (lsp/graph->dgml g)))

          (= from "lua-lsp")
          (let [g (lsp/extract-lua {:root-path (first args)})]
            (sdgml/write-to-file output-file (lsp/graph->dgml g)))

          (= from "rust-lsp")
          (let [g (lsp/extract-rust {:root-path (first args)})]
            (sdgml/write-to-file output-file (lsp/graph->dgml g)))

          (= from "ts-lsp")
          (let [g (lsp/extract-typescript {:root-path (first args)})]
            (sdgml/write-to-file output-file (lsp/graph->dgml g)))

          (= from "zig-lsp")
          (let [g (lsp/extract-zig {:root-path (first args)})]
            (sdgml/write-to-file output-file (lsp/graph->dgml g)))

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

          (= from "dgml")
          (let [g (sdgml/load-graph (first args))]
            (sdgml/write-to-file output-file (sdgml/graph->dgml g)))

          (= from "scip")
          ((requiring-resolve `scip/extract)
           {:index-file (first args)
            :output-file output-file})

          (= from "ts-scip")
          ((requiring-resolve `scip/extract-ts-scip)
           {:dir (first args)
            :output-file output-file})

          (= from "go-scip")
          ((requiring-resolve `scip/extract-scip-go)
           {:dir (first args)
            :output-file output-file})

          (= from "python-scip")
          ((requiring-resolve `scip/extract-scip-py)
           {:dir (first args)
            :output-file output-file})

          (= from "ruby-scip")
          ((requiring-resolve `scip/extract-scip-rb)
           {:dir (first args)
            :output-file output-file})

          (= from "clj")
          (stratify/extract (merge opts {:source-paths args
                                         :output-file output-file}))

          :else
          (print-help))))))

(defn clj-main-report-error [t]
  (clj-main/report-error t :target (System/getProperty "clojure.main.report" "file")))

(defn report-error [t]
  (cond
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

  (cli/parse-args [] {:spec cli-spec})

  (main* "--studio" "src")

  (main* "-f" "go-lsp" "test/resources/code/go/greeting")

  (main* "-f" "lua-lsp" "test/resources/code/lua/greeting")
  (main* "--studio" "-f" "lua-lsp" "test/resources/code/lua/greeting")

  (main* "-f" "rust-lsp" "test/resources/code/rust/greeting")

  (main* "-f" "zig-lsp" "test/resources/code/zig/greeting")

  (main* "--studio" "-f" "overarch" "test/resources/overarch/model.edn")

  (main* "-f" "dot" "test/resources/graphviz/simple.dot")
  (main* "--studio" "-f" "dot" "test/resources/graphviz/simple.dot")

  (main* "-f" "scip" "test/resources/scip/go.scip")
  (main* "--studio" "-f" "scip" "test/resources/scip/go.scip"))

