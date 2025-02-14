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
  (let [nodes (->> (:content data)
                   (filter (comp #{::xdgml/Nodes} :tag))
                   (first))
        links (->> (:content data)
                   (filter (comp #{::xdgml/Links} :tag))
                   (first))
        node-attrs (->> (:content nodes)
                        (filter (comp #{::xdgml/Node} :tag))
                        (map (juxt (comp :Id :attrs) :attrs))
                        (into {}))
        parents (->> (:content links)
                     (filter (comp #{::xdgml/Link} :tag))
                     (filter (comp #{"Contains"} :Category :attrs))
                     (map (fn [{:keys [attrs]}]
                            (let [{:keys [Source Target]} attrs]
                              [Target Source])))
                     (into {}))
        get-namespace (fn [node-id]
                        (if (= (get-in node-attrs [node-id :Category]) "Namespace")
                          node-id
                          (get parents node-id)))
        edges (->> (:content links)
                   (filter (comp #{::xdgml/Link} :tag))
                   (remove (comp #{"Contains"} :Category :attrs))
                   (map (fn [{:keys [attrs]}]
                          (let [{:keys [Source Target]} attrs]
                            [(get-namespace Source) (get-namespace Target)]))))]
    (-> (lg/digraph)
        (lg/add-edges* edges))))

(defn load-graph [input-file]
  (let [data (with-open [rdr (io/reader input-file)]
               (xml/parse rdr))]
    (->graph data)))

(comment
  (load-graph "test/resources/nested/output-default.dgml"))
