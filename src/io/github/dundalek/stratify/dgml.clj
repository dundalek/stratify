(ns io.github.dundalek.stratify.dgml
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [loom.graph :as lg]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias xdgml]))

(defn write-to-file [output-file dgml-data]
  (try
    (if (instance? java.io.Writer output-file)
      (xml/indent dgml-data output-file)
      (with-open [out (io/writer output-file)]
        (xml/indent dgml-data out)))
    (catch Throwable t
      (throw (ex-info "Failed to write output file." {:code ::failed-to-write} t)))))

(defn ->graph [data]
  (assert (= ::xdgml/DirectedGraph (:tag data)))
  (let [links (->> (:content data)
                   (filter (comp #{::xdgml/Links} :tag))
                   (first))
        parents (->> (:content links)
                     (filter (comp #{::xdgml/Link} :tag))
                     (filter (comp #{"Contains"} :Category :attrs))
                     (map (fn [{:keys [attrs]}]
                            (let [{:keys [Source Target]} attrs]
                              [Target Source])))
                     (into {}))
        parent? (set (vals parents))
        get-namespace (fn [node-id]
                        (if (parent? node-id)
                          node-id
                          (get parents node-id)))
        edges (->> (:content links)
                   (filter (comp #{::xdgml/Link} :tag))
                   (remove (comp #{"Contains"} :Category :attrs))
                   (map (fn [{:keys [attrs]}]
                          (let [{:keys [Source Target]} attrs]
                            [(get-namespace Source) (get-namespace Target)])))
                   (remove (fn [[from to]]
                             (= from to))))]
    (-> (lg/digraph)
        (lg/add-edges* edges))))

(defn load-graph [input-file]
  (with-open [rdr (io/reader input-file)]
    (let [data (xml/parse rdr)]
      (->graph data))))

(comment
  (load-graph "test/resources/nested/output-default.dgml"))
