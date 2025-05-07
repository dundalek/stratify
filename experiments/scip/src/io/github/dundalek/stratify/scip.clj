(ns io.github.dundalek.stratify.scip
  {:clj-kondo/config '{:lint-as {pronto.core/defmapper clojure.core/def}}}
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [io.github.dundalek.stratify.scip.extractors :as extractors]
   [pronto.core :as p]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml])
  (:import
   (com.sourcegraph Scip$Index Scip$SymbolRole)
   (java.nio.file Files)))

(p/defmapper sm [Scip$Index])

(defn read-scip-index [index-file]
  (let [path (.toPath (io/file index-file))
        arr (Files/readAllBytes path)]
    (p/bytes->proto-map sm Scip$Index arr)))

(defn- symbol->label [s]
  (last (str/split s #"\s+")))

(defn ->graph [index]
  (let [filtered-documents (->> (:documents index)
                                ;; files starting with "../" point to build caches, so we filter them out
                                (remove #(str/starts-with? (:relative_path %) "../")))
        symbol-path-idx (->> filtered-documents
                             (mapcat (fn [{:keys [relative_path symbols]}]
                                       (->> symbols
                                            (map (fn [{:keys [symbol]}]
                                                   [symbol relative_path])))))
                             (into {}))
        adj (->> filtered-documents
                 (mapcat (fn [{:keys [relative_path occurrences]}]
                           (->>  occurrences
                                 (keep (fn [{:keys [symbol_roles symbol]}]
                                         (when (or (= symbol_roles Scip$SymbolRole/ReadAccess_VALUE)
                                                   ;; UnspecifiedSymbolRole is used by ruby-scip for references
                                                   (= symbol_roles Scip$SymbolRole/UnspecifiedSymbolRole_VALUE))
                                           (when-some [target-path (get symbol-path-idx symbol)]
                                             [relative_path target-path])))))))
                 (reduce (fn [m [from to]]
                           (update m from (fnil conj #{}) to))
                         {}))]
    {:adj adj}))

(defn ->dgml [index]
  (let [nodes (->> (:documents index)
                   (mapcat :symbols)
                   (map (fn [{:keys [symbol]}]
                          (xml/element ::dgml/Node
                                       {:Id symbol
                                        :Label (symbol->label symbol)
                                        :Name symbol}))))
        references (->> (:documents index)
                        (reduce (fn [result {:keys [occurrences]}]
                                  (loop [[definition & xs] occurrences
                                         result result]
                                    (if (nil? definition)
                                      result
                                      (let [[references xs] (->> xs (split-with (fn [{:keys [symbol_roles]}] (not= symbol_roles Scip$SymbolRole/Definition_VALUE))))]
                                        (assert (= (:symbol_roles definition) Scip$SymbolRole/Definition_VALUE))
                                        (recur xs (assoc result
                                                         (:symbol definition)
                                                         (->> references (map :symbol) set)))))))
                                {}))
        links (->> references
                   (mapcat (fn [[source targets]]
                             (->> targets
                                  (map (fn [target]
                                         (xml/element ::dgml/Link {:Source source :Target target})))))))]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {} nodes)
                 (xml/element ::dgml/Links {} links))))

(defn extract [{:keys [index-file output-file]}]
  (let [index (read-scip-index index-file)
        data (->dgml index)]
    (if (instance? java.io.Writer output-file)
      (xml/indent data output-file)
      (with-open [out (io/writer output-file)]
        (xml/indent data out)))))

(comment
  (extractors/extract-go {:dir "test/resources/sample-go" :output-file "target/scip/go.scip"})
  ; (extractors/extract-js {:dir "test/resources/sample-js" :output-file "target/scip/js.scip"})
  (extractors/extract-py {:dir "test/resources/sample-py" :output-file "target/scip/py.scip"})
  ; (extractors/extract-rb {:dir "test/resources/sample-rb" :output-file "target/scip/rb.scip"})
  (extractors/extract-rs {:dir "test/resources/sample-rs" :output-file "target/scip/rs.scip"})
  (extractors/extract-ts {:dir "test/resources/sample-ts" :output-file "target/scip/ts.scip"})
  (extractors/extract-ts {:dir "test/resources/sample-ts-simple" :output-file "target/scip/ts-simple.scip"})

  (def index (read-scip-index "target/scip/go.scip"))
  (def index (read-scip-index "target/scip/py.scip"))
  (def index (read-scip-index "target/scip/rs.scip"))
  (def index (read-scip-index "target/scip/ts.scip"))
  (def index (read-scip-index "target/scip/ts-simple.scip"))

  (tap> index)

  (->graph index)
  (->dgml index)

  (extract {:index-file "target/scip/go.scip"
            :output-file "target/dgml/scip-go.dgml"})

  (extract {:index-file "index.scip"
            :output-file "target/dgml/scip-clang-postgress.dgml"})

  (extract {:index-file "/home/me/Downloads/git/postgres/index.scip"
            :output-file "target/dgml/scip-clang-postgres.dgml"})

  (extract {:index-file "/home/me/Downloads/git/neovim/index.scip"
            :output-file "target/dgml/scip-clang-neovim.dgml"}))

(comment
  ;; Symbol identifier grammar: https://github.com/sourcegraph/scip/blob/b469406e85b91a947c266ec84835ab81eaa6150e/scip.proto#L156
  (let [[scheme manager package-name version descriptor] (str/split "scip-go gomod sample-go d0e9b3a9efb9 sample-go/" #"\s+" 5)]
    {:scheme scheme
     :manager manager
     :package-name package-name
     :version version
     :descriptor descriptor})

  ;; either ends with / which is namespace or . which is a term
  ;; split by / to get segments
  ;; don't unescape ` for now

  (defn unescape [s]
    ;; TODO: unescape properly
    ;; for now just naively stripping backticks
    (str/replace s "`" ""))

  (let [descriptor #_"`sample-go/greet`/"
        "`sample-go/greet`/TheWorld()."]
    (str/split (unescape descriptor) #"/" -1)))
