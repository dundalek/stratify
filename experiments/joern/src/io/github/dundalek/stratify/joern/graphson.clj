(ns io.github.dundalek.stratify.joern.graphson
  (:require
   [clojure.data.xml :as xml]
   [clojure.string]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.dgml-rdf :as dgf]
   [io.github.dundalek.stratify.style :as style]
   [jsonista.core :as j]
   [quoll.rdf :as qrdf :refer [RDF-TYPE]]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml])
  (:import
   [java.net URLEncoder URLDecoder]
   [java.nio.charset StandardCharsets]))

(defn- extract-property-value [vertex-property]
  (-> vertex-property
      (get "@value")
      (get "@value")
      first))

(defn- parse-vertex-properties [vertex-label properties]
  (->> properties
       (map (fn [[k v]]
              [(keyword (str "node__" vertex-label "__" k))
               (extract-property-value v)]))
       (into {})))

(defn- parse-edge-properties [properties]
  (->> properties
       (map (fn [[k v]]
              [(keyword k) (extract-property-value v)]))
       (into {})))

;; RDF namespace definitions
(def joern-ns-iri "http://dundalek.github.io/stratify/joern-ns/")
(def joern-ns-prefix "joern-ns")

(defn joern-iri [local]
  (qrdf/iri (str joern-ns-iri local) joern-ns-prefix local))

(def JOERN-NS-VERTEX (joern-iri "Vertex"))
(def JOERN-NS-EDGE (joern-iri "Edge"))
(def JOERN-NS-METHOD (joern-iri "Method"))
(def JOERN-NS-CALL (joern-iri "Call"))
(def JOERN-NS-METHOD-CALL (joern-iri "MethodCall"))
(def JOERN-NS-CONTAINMENT (joern-iri "Containment"))

;; Convert property key to IRI
(defn property->iri [prop-key]
  (joern-iri (name prop-key)))

;; Create entity IRI from vertex/edge ID
(defn entity-iri [id]
  (let [encoded (URLEncoder/encode (str id) StandardCharsets/UTF_8)]
    (qrdf/iri (str joern-ns-iri "entity/" encoded) joern-ns-prefix (str "entity/" encoded))))

(defn graphson->rdf [data]
  "Convert GraphSON data to RDF normal-form containing all vertices and edges with their properties."
  (let [{:strs [edges vertices]} (get data "@value")
        rdf-graph (atom {})]

    ;; Process vertices
    (doseq [vertex vertices]
      (let [vertex-id (get-in vertex ["id" "@value"])
            label (get vertex "label")
            properties (parse-vertex-properties label (get vertex "properties" {}))
            entity-iri (entity-iri vertex-id)

            ;; Create RDF properties for the vertex
            rdf-props (cond-> {RDF-TYPE #{JOERN-NS-VERTEX}
                               (joern-iri "label") #{label}
                               (joern-iri "id") #{vertex-id}}
                        ;; Add all vertex properties
                        (seq properties) (merge (into {} (map (fn [[k v]]
                                                                [(property->iri k) #{v}])
                                                              properties))))]
        (swap! rdf-graph assoc entity-iri rdf-props)))

    ;; Process edges
    (doseq [edge edges]
      (let [edge-id (get-in edge ["id" "@value"])
            source-id (get-in edge ["outV" "@value"])
            target-id (get-in edge ["inV" "@value"])
            label (get edge "label")
            properties (parse-edge-properties (get edge "properties" {}))
            edge-iri (entity-iri edge-id)

            ;; Create RDF properties for the edge
            rdf-props (cond-> {RDF-TYPE #{JOERN-NS-EDGE}
                               (joern-iri "label") #{label}
                               (joern-iri "id") #{edge-id}
                               (joern-iri "source") #{(entity-iri source-id)}
                               (joern-iri "target") #{(entity-iri target-id)}}
                        ;; Add all edge properties
                        (seq properties) (merge (into {} (map (fn [[k v]]
                                                                [(property->iri k) #{v}])
                                                              properties))))]
        (swap! rdf-graph assoc edge-iri rdf-props)))

    @rdf-graph))

(defn rdf-graph->rdf-dgml [rdf-graph]
  "Create RDF DGML graph from RDF normal-form graph.
   Only creates METHOD nodes, filename nodes, method-call links, and containment links."
  (let [!id-generator (atom 0)
        generate-id! #(qrdf/unsafe-blank-node (str "b" (swap! !id-generator inc)))
        dgml-graph (atom {})]

    ;; Extract vertices and edges from RDF graph
    (let [vertices (->> rdf-graph
                        (filter (fn [[iri props]]
                                  (contains? (get props RDF-TYPE) JOERN-NS-VERTEX)))
                        (map (fn [[iri props]]
                               (let [id (first (get props (joern-iri "id")))
                                     label (first (get props (joern-iri "label")))]
                                 {:iri iri :id id :label label :props props}))))

          edges (->> rdf-graph
                     (filter (fn [[iri props]]
                               (contains? (get props RDF-TYPE) JOERN-NS-EDGE)))
                     (map (fn [[iri props]]
                            (let [id (first (get props (joern-iri "id")))
                                  label (first (get props (joern-iri "label")))
                                  source-iri (first (get props (joern-iri "source")))
                                  target-iri (first (get props (joern-iri "target")))]
                              {:iri iri :id id :label label :source-iri source-iri :target-iri target-iri :props props}))))

          ;; Extract METHOD vertices
          method-vertices (->> vertices
                               (filter #(= "METHOD" (:label %)))
                               (map (fn [vertex]
                                      (let [props (:props vertex)]
                                        {:id (:id vertex)
                                         :name (first (get props (property->iri :node__METHOD__NAME)))
                                         :full-name (first (get props (property->iri :node__METHOD__FULL_NAME)))
                                         :filename (first (get props (property->iri :node__METHOD__FILENAME)))}))))

          ;; Extract CALL vertices
          call-vertices (->> vertices
                             (filter #(= "CALL" (:label %)))
                             (map :id))

          ;; Build edge index by source and target IDs
          vertices-by-id (->> vertices
                              (map (fn [vertex] [(:id vertex) vertex]))
                              (into {}))

          ;; Helper to extract ID from entity IRI as number
          iri->id (fn [iri]
                    (-> iri str (clojure.string/split #"/") last java.net.URLDecoder/decode Long/valueOf))

          ;; Group edges by source/target but keep all edges (not just the last one)
          edges-by-source-target (->> edges
                                      (group-by (fn [edge]
                                                  (let [source-id (iri->id (:source-iri edge))
                                                        target-id (iri->id (:target-iri edge))]
                                                    [source-id target-id]))))

;; Extract method call relationships
          ;; Find CONTAINS edges: METHOD → CALL
          contains-edges (->> edges-by-source-target
                              (mapcat (fn [[[source target] edge-list]]
                                        (->> edge-list
                                             (filter (fn [edge]
                                                       (and (= "CONTAINS" (:label edge))
                                                            (= "METHOD" (get-in vertices-by-id [source :label]))
                                                            (= "CALL" (get-in vertices-by-id [target :label])))))
                                             (map (fn [edge] [source target]))))))

          ;; Find CALL edges: CALL → METHOD
          call-edges (->> edges-by-source-target
                          (mapcat (fn [[[source target] edge-list]]
                                    (->> edge-list
                                         (filter (fn [edge]
                                                   (and (= "CALL" (:label edge))
                                                        (= "CALL" (get-in vertices-by-id [source :label]))
                                                        (= "METHOD" (get-in vertices-by-id [target :label])))))
                                         (map (fn [edge] [source target]))))))

          ;; Build method call relationships: METHOD → METHOD via CALL
          method-call-edges (->> contains-edges
                                 (map (fn [[method-id call-id]]
                                        (->> call-edges
                                             (filter (fn [[call-source call-target]]
                                                       (= call-source call-id)))
                                             (map (fn [[call-source call-target]]
                                                    [method-id call-target])))))
                                 (apply concat))

          filenames (->> method-vertices
                         (map :filename)
                         (filter some?)
                         (distinct))]

      ;; Create DGML nodes in RDF for METHOD vertices only
      (doseq [method method-vertices]
        (let [entity-iri (entity-iri (:id method))
              rdf-props (cond-> {RDF-TYPE #{dgf/DGML-NS-NODE}
                                 (dgf/dgml-iri "Id") #{(:id method)}
                                 (dgf/dgml-iri "Category") #{"Function"}}
                          (:name method) (assoc dgf/DGML-NS-LABEL #{(:name method)})
                          (:full-name method) (assoc (dgf/dgml-iri "Name") #{(:full-name method)}))]
          (swap! dgml-graph assoc entity-iri rdf-props)))

      ;; Create DGML nodes in RDF for filenames
      (doseq [filename filenames]
        (let [file-iri (entity-iri filename)
              file-props {RDF-TYPE #{dgf/DGML-NS-NODE}
                          (dgf/dgml-iri "Id") #{filename}
                          dgf/DGML-NS-LABEL #{filename}
                          (dgf/dgml-iri "Category") #{"Namespace"}
                          (dgf/dgml-iri "Group") #{"Expanded"}}]
          (swap! dgml-graph assoc file-iri file-props)))

      ;; Create DGML links for method calls
      (doseq [[source-id target-id] method-call-edges]
        (let [link-iri (generate-id!)
              link-props {RDF-TYPE #{dgf/DGML-NS-LINK}
                          dgf/DGML-NS-SOURCE #{(entity-iri source-id)}
                          dgf/DGML-NS-TARGET #{(entity-iri target-id)}}]
          (swap! dgml-graph assoc link-iri link-props)))

      ;; Create DGML links for containment (file contains method)
      (doseq [method method-vertices
              :let [filename (:filename method)]
              :when filename]
        (let [containment-iri (generate-id!)
              containment-props {RDF-TYPE #{dgf/DGML-NS-LINK}
                                 dgf/DGML-NS-SOURCE #{(entity-iri filename)}
                                 dgf/DGML-NS-TARGET #{(entity-iri (:id method))}
                                 (dgf/dgml-iri "Category") #{"Contains"}}]
          (swap! dgml-graph assoc containment-iri containment-props))))

    @dgml-graph))

(defn extract [{:keys [input-file output-file]}]
  (let [data (j/read-value (slurp input-file) j/default-object-mapper)
        rdf-graph (graphson->rdf data)
        rdf-dgml (rdf-graph->rdf-dgml rdf-graph)
        prefix "http://dundalek.github.io/stratify/joern-ns/entity/"
        dgml-base (dgf/rdf->dgml rdf-dgml {:prefix prefix})
        ;; Add styles to match original behavior
        dgml-with-styles (update dgml-base :content
                                 (fn [content]
                                   (conj (vec content)
                                         (xml/element ::dgml/Styles {} style/styles))))]
    (sdgml/write-to-file output-file dgml-with-styles)))

(comment
  ;; joern-export --repr all --format graphson --out graphson-go tmp/cpg-go.bin

  ;; Usage example:
  (extract {:input-file "test/resources/joern-cpg/out-go/export.json"
            :output-file "test/output/graphson-go.dgml"})

  ;; not keywordizing because representation has things like keys like @value @type
  (def gg (j/read-value (slurp "test/resources/joern-cpg/out-go/export.json") j/default-object-mapper))
  (def gg (j/read-value (slurp "test/resources/joern-cpg/out-go/export.json") j/keyword-keys-object-mapper))

  (def at-value (keyword "@value"))
  (def at-type (keyword "@type"))

  (let [{:strs [edges vertices]} (get gg "@value")]
    [(count edges)
     (count vertices)])

  (def edges (get-in gg ["@value" "edges"]))
  (def vertices (get-in gg ["@value" "vertices"]))

  (count vertices)

  (->> edges
       (map #(get % "label"))
       frequencies
       (sort-by val)
       reverse)

  (->> vertices
       (mapcat keys)
       frequencies
       (sort-by val)
       reverse)

  (-> (group-by #(get % "label") vertices)
      (update-vals (fn [items]
                     (->> items
                          (map #(get % "properties"))
                          (mapcat keys)
                          frequencies))))

  (-> (group-by #(get % "label") edges)
      (update-vals (fn [items]
                     (->> items
                          (map #(get % "properties"))
                          (mapcat keys)
                          frequencies))))

  ;; CALL can be both edge label or vertex label
  (clojure.set/intersection
   (->> edges (map #(get % "label")) set)
   (->> vertices (map #(get % "label")) set))
   ; #{"CALL"}

  ;; node properties and edge properties do not conflict
  ;; edges only have extra "EdgeProperty" property
  (clojure.set/intersection
   (->> edges (map #(get % "properties")) (mapcat keys) set)
   (->> vertices (map #(get % "properties")) (mapcat keys) set))
  ; #{}

  (->> edges
       (map #(get % "properties"))
       (filter seq))

  (def input-file "test/resources/joern-cpg/out-go/export.json")
  (def input-file "../../test/resources/samples/graphson-java/export.json")

  (do
    (def input-file "/home/me/dl/git/dddsample-core/out/export.json")
    (def output-file "../../../../shared/joern-java-sample.dgml"))

  (extract {:input-file input-file
            :output-file output-file})

  (let [data (j/read-value (slurp input-file) j/default-object-mapper)
        rdf-graph (graphson->rdf data)
        rdf-dgml (rdf-graph->rdf-dgml rdf-graph)]
    rdf-graph
    rdf-dgml)

  (extract {:input-file input-file
            :output-file "../../../../shared/joern-java-sample.dgml"}))

