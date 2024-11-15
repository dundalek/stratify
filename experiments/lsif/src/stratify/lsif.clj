(ns stratify.lsif
  (:require
   [clojure.java.io :as io]
   [jsonista.core :as j]
   [clojure.data.xml :as xml]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

(defn ->dgml [items]
  (let [nodes (->> items
                   (filter (comp #{"vertex"} :type))
                   (map (fn [{:keys [id label] :as vertex}]
                          (xml/element ::dgml/Node
                                       (-> vertex
                                           (dissoc :contents)
                                           (update-vals pr-str)
                                           (merge
                                            {:Id id
                                             :Label label}))))))
        links (->> items
                   (filter (comp #{"edge"} :type))
                   (mapcat (fn [{:keys [inV inVs outV label] :as edge}]
                             (->> (or inVs [inV])
                                  (map (fn [source]
                                         (xml/element ::dgml/Link
                                                      (merge (update-vals edge pr-str)
                                                             {:Source source
                                                              :Target outV
                                                              :Label label}))))))))]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {} nodes)
                 (xml/element ::dgml/Links {} links))))

(comment
  (with-open [rdr (io/reader "ts-simple.lsif")]
    (def items
      (->> (line-seq rdr)
           (map #(j/read-value % j/keyword-keys-object-mapper))
           (doall))))

  (let [output-file "../../../../shared/simpl-ts-lsif.dgml"
        data (->dgml items)]
    (with-open [out (io/writer output-file)]
      (xml/indent data out)))

  (->> items
       (map :type)
       frequencies)
  ; => {"vertex" 43, "edge" 39}

  (->> items
       (filter (comp #{"vertex"} :type))
       (mapcat keys)
       frequencies
       (sort-by val)
       reverse)

  (->> items
       (filter (comp #{"vertex"} :type)))

  (->> items
       (filter (comp #{"edge"} :type))
       ; (map :label)
       (mapcat keys)
       frequencies
       (sort-by val)
       reverse)

  [["item" 11]
   ["next" 7]
   ["textDocument/definition" 4]
   ["contains" 4]
   ["moniker" 4]
   ["textDocument/references" 4]
   ["textDocument/hover" 3]
   ["textDocument/documentSymbol" 1]
   ["textDocument/foldingRange" 1]])

