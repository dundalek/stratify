(ns stratify.main-jvm
  (:require
   [babashka.cli :as cli]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.main :as clj-main]
   [clojure.repl.deps :as deps]
   [io.github.dundalek.stratify.codecharta :as-alias codecharta]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.graphviz :as-alias graphviz]
   [io.github.dundalek.stratify.joern :as-alias joern]
   [io.github.dundalek.stratify.lsp :as lsp]
   [io.github.dundalek.stratify.overarch :as-alias overarch]
   [io.github.dundalek.stratify.report :as-alias report]
   [io.github.dundalek.stratify.scip :as-alias scip]
   [io.github.dundalek.stratify.studio.main :as-alias studio]
   [io.github.dundalek.stratify.treesitter-lua :as-alias treesitter-lua]
   [stratify.main-clj :as main-clj]))

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
  ((requiring-resolve `studio/open) g))

(defn- run-jvm-only
  "Handle JVM-only formats. Returns ::unhandled if not a JVM-only format."
  [{:keys [opts args]}]
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

      (and studio (= from "lua-ts"))
      (do
        (add-deps "treesitter")
        (let [g ((requiring-resolve `treesitter-lua/extract-lua)
                 {:root-path (first args)})]
          (open-studio g)))

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

      (and studio (= from "scip"))
      (let [g ((requiring-resolve `scip/load-graph) {:index-file (first args)})]
        (open-studio g))

      (and studio (= from "ts-scip"))
      (let [g ((requiring-resolve `scip/load-graph-ts-scip)
               {:dir (first args)
                :flat-namespaces (:flat-namespaces opts)})]
        (open-studio g))

      (and studio (= from "go-scip"))
      (let [g ((requiring-resolve `scip/load-graph-scip-go)
               {:dir (first args)
                :flat-namespaces (:flat-namespaces opts)})]
        (open-studio g))

      (and studio (= from "python-scip"))
      (let [g ((requiring-resolve `scip/load-graph-scip-py)
               {:dir (first args)
                :flat-namespaces (:flat-namespaces opts)})]
        (open-studio g))

      (and studio (= from "ruby-scip"))
      (let [g ((requiring-resolve `scip/load-graph-scip-rb)
               {:dir (first args)
                :flat-namespaces (:flat-namespaces opts)})]
        (open-studio g))

      (= from "lua-ts")
      (do
        (add-deps "treesitter")
        (let [g ((requiring-resolve `treesitter-lua/extract-lua)
                 {:root-path (first args)})]
          (sdgml/write-to-file output-file (lsp/graph->dgml g))))

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

      (= from "c-joern")
      (do
        (add-deps "joern-c")
        ((requiring-resolve `joern/extract-c)
         {:root-path (first args)
          :output-file output-file}))

      (= from "go-joern")
      (do
        (add-deps "joern-go")
        ((requiring-resolve `joern/extract-go)
         {:root-path (first args)
          :output-file output-file}))

      (= from "java-joern")
      (do
        (add-deps "joern-java")
        ((requiring-resolve `joern/extract-java)
         {:root-path (first args)
          :output-file output-file}))

      (= from "js-joern")
      (do
        (add-deps "joern-javascript")
        ((requiring-resolve `joern/extract-javascript)
         {:root-path (first args)
          :output-file output-file}))

      :else (main-clj/print-help))))

(defn main* [& args]
  (let [parsed (main-clj/parse-args args)]
    (when (= ::main-clj/unhandled (main-clj/run parsed))
      (run-jvm-only parsed))))

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

  (cli/parse-args [] {:spec main-clj/cli-spec})

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
