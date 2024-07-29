(ns stratify.main
  (:require
   [babashka.cli :as cli]
   [io.github.dundalek.stratify.internal :as stratify]))

(def cli-spec
  {:out {:alias :o
         :ref "<file>"
         :desc "Output file, default \"-\" standard output"
         :default "-"}
   :flat-namespaces {:coerce :boolean
                     :desc "Render flat namespaces instead of a nested hierarchy"}
   :include-dependencies {:coerce :boolean
                          :desc "Include links to library dependencies"}
   :help {:alias :h
          :desc "Print this help message and exit"}})

(defn print-help []
  (println "Extract DGML graph from source code")
  (println)
  (println "Usage: stratify <options> <src-paths>")
  (println)
  (println "Options:")
  (println (cli/format-opts {:spec cli-spec})))

(defn -main [& args]
  (let [parsed (cli/parse-args args {:spec cli-spec})
        {:keys [opts args]} parsed]
    (if (or (:help opts) (:h opts) (empty? args))
      (print-help)
      (let [{:keys [out]} opts
            output-file (if (= out "-") *out* out)]
        (stratify/extract (merge opts {:source-paths args
                                       :output-file output-file}))))))

(comment
  (-main "--help")

  (-main "src")
  (-main "--out" "out.dgml" "src")
  (-main "--flat-namespaces" "src"))
