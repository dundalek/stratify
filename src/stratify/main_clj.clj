(ns stratify.main-clj
  "Shared implementation that works in both JVM and babashka.
   Handles: from=clj, from=dgml, from=pulumi, to=dep-tree, LSP extractors, --studio with clj/dgml/lsp"
  (:require
   [babashka.cli :as cli]
   [clojure.string :as str]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.gabotechs-dep-tree :as dep-tree]
   [io.github.dundalek.stratify.internal :as stratify]
   [io.github.dundalek.stratify.lsp :as lsp]
   [io.github.dundalek.stratify.pulumi :as pulumi]
   [io.github.dundalek.stratify.studio.main :as-alias studio]))

(def ^:private lsp-source-formats
  #{"c-lsp" "go-lsp" "lua-lsp" "rust-lsp" "ts-lsp" "zig-lsp"})

(def ^:private language-extractors
  #{"c-joern" "c-lsp"
    "clj"
    "go-joern" "go-lsp" "go-scip"
    "lua-lsp" "lua-ts"
    "python-scip"
    "ruby-scip"
    "rust-lsp"
    "ts-scip" "ts-lsp"
    "zig-lsp"})

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

(def cli-spec
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

(defn print-help []
  (println "Extract DGML graph from source code")
  (println)
  (println "Usage: stratify <options> <src-paths>")
  (println)
  (println "Options:")
  (println (cli/format-opts {:spec cli-spec})))

(defn- open-studio [g]
  ((requiring-resolve `studio/open) g))

(def ^:private lsp-extractors
  {"c-lsp" lsp/extract-c
   "go-lsp" lsp/extract-go
   "lua-lsp" lsp/extract-lua
   "rust-lsp" lsp/extract-rust
   "ts-lsp" lsp/extract-typescript
   "zig-lsp" lsp/extract-zig})

(defn parse-args [args]
  (cli/parse-args args {:spec cli-spec}))

(defn run
  "Returns ::unhandled if format not supported by this module."
  [parsed]
  (let [{:keys [opts args]} parsed]
    (if (or (:help opts) (:h opts) (empty? args))
      (print-help)
      (let [{:keys [out from studio]} opts
            output-file (if (= out "-") *out* out)]
        (cond
          (and studio (= from "dgml"))
          (let [g (sdgml/load-graph (first args))]
            (open-studio g))

          (and studio (= from "clj"))
          (let [g (stratify/extract-graph (merge opts {:source-paths args}))]
            (open-studio g))

          (and studio (contains? lsp-source-formats from))
          (let [extract-fn (get lsp-extractors from)
                g (extract-fn {:root-path (first args)})]
            (open-studio g))

          (= from "dgml")
          (let [g (sdgml/load-graph (first args))]
            (sdgml/write-to-file output-file (sdgml/graph->dgml g)))

          (and (= from "clj") (= (:to opts) "dgml"))
          (stratify/extract (merge opts {:source-paths args
                                         :output-file output-file}))

          (contains? lsp-source-formats from)
          (let [extract-fn (get lsp-extractors from)
                g (extract-fn {:root-path (first args)})]
            (sdgml/write-to-file output-file (lsp/graph->dgml g)))

          (= (:to opts) "dep-tree")
          (dep-tree/extract {:source-paths args
                             :output-file output-file})

          (= from "pulumi")
          (pulumi/extract {:input-file (first args)
                           :output-file output-file})

          :else ::unhandled)))))
