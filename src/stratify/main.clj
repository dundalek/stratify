(ns stratify.main
  (:require
   [babashka.cli :as cli]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.repl.deps :as deps]
   [io.github.dundalek.stratify.internal :as stratify]
   [io.github.dundalek.stratify.metrics :as-alias metrics]))

(def cli-spec
  {:out {:alias :o
         :ref "<file>"
         :desc "Output file, default \"-\" standard output"
         :default "-"}
   :flat-namespaces {:coerce :boolean
                     :desc "Render flat namespaces instead of a nested hierarchy"}
   :include-dependencies {:coerce :boolean
                          :desc "Include links to library dependencies"}
   :metrics {:coerce :boolean
             :desc "Calculate and serve namespace metrics report"}
   :help {:alias :h
          :desc "Print this help message and exit"}})

(defn print-help []
  (println "Extract DGML graph from source code")
  (println)
  (println "Usage: stratify <options> <src-paths>")
  (println)
  (println "Options:")
  (println (cli/format-opts {:spec cli-spec})))

(defn ensure-dynamic-context-classloader! []
  (let [cl (.getContextClassLoader (Thread/currentThread))]
    (when-not (instance? clojure.lang.DynamicClassLoader cl)
      (.setContextClassLoader (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl)))))

(defn add-deps [feature]
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
      (let [{:keys [out metrics]} opts]
        (if metrics
          (do
            (add-deps "metrics")
            ((requiring-resolve `metrics/report!)
             {:source-paths args
              :output-path (when (not= out "-") out)}))
          (let [output-file (if (= out "-") *out* out)]
            (stratify/extract (merge opts {:source-paths args
                                           :output-file output-file}))))))))

(comment
  (-main "--help")

  (-main "src")
  (-main "--out" "out.dgml" "src")
  (-main "--flat-namespaces" "src"))
