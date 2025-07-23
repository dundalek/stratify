(ns io.github.dundalek.stratify.graphviz-rdf
  (:require
   [clojure.set]
   [dorothy.core :as-alias dc]
   [io.github.dundalek.stratify.dgml-rdf :as dgf :refer [DGML-NS-LABEL
                                                         DGML-NS-LINK
                                                         DGML-NS-NODE
                                                         DGML-NS-SOURCE
                                                         DGML-NS-TARGET]]
   [quoll.raphael.core :as raphael]
   [quoll.rdf :as qrdf :refer [RDF-TYPE]]
   [theodora.core :as theodora]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

(def graphviz-ns-prefix "graphviz")
(def graphviz-ns-iri "http://dundalek.github.io/stratify/graphviz-ns/")

(def common-prefixes {:graphviz graphviz-ns-iri})

(defn graphviz-iri [local]
  (qrdf/curie common-prefixes graphviz-ns-prefix local))

(def GRAHPVIZ-NODE (graphviz-iri "Node"))
(def GRAHPVIZ-EDGE (graphviz-iri "Edge"))
(def GRAHPVIZ-SOURCE (graphviz-iri "source"))
(def GRAHPVIZ-TARGET (graphviz-iri "target"))
(def GRAHPVIZ-LABEL (graphviz-iri "label"))

(defn graphviz->rdf [{:keys [digraph prefix]}]
  (let [{:keys [statements]} digraph
        nodes (->> statements
                   (filter #(= ::dc/node (:type %))))
        edges (->> statements
                   (filter #(= ::dc/edge (:type %)))
                   (mapcat (fn [{:keys [node-ids]}]
                             (->> node-ids
                                  (map :id)
                                  (partition 2 1)))))
        default-prefix ""
        prefixes {default-prefix prefix}
        node-id->iri (fn [id] (qrdf/curie prefixes default-prefix id))
        !generator (atom (raphael/new-generator))
        new-node! (fn []
                    (let [[new-generator node] (raphael/new-node @!generator)]
                      (reset! !generator new-generator)
                      node))

        ;; Get all node IDs from both explicit nodes and edges
        explicit-node-ids (set (map #(:id (:id %)) nodes))
        edge-node-ids (set (mapcat (fn [[from to]] [from to]) edges))
        all-node-ids (clojure.set/union explicit-node-ids edge-node-ids)

        ;; Generate RDF for all nodes (explicit + implicit from edges)
        node-entities (->> all-node-ids
                           (map (fn [node-id]
                                  (let [iri (node-id->iri node-id)
                                        ;; Find explicit node attrs if exists
                                        explicit-node (first (filter #(= (:id (:id %)) node-id) nodes))
                                        label (or (when explicit-node (get (:attrs explicit-node) "label"))
                                                  node-id)]
                                    {iri (cond-> {RDF-TYPE #{GRAHPVIZ-NODE}
                                                  GRAHPVIZ-LABEL #{label}})})))
                           (reduce merge {}))

        ;; Generate RDF for edges
        edge-entities (->> edges
                           (map (fn [[from to]]
                                  {(new-node!) {RDF-TYPE #{GRAHPVIZ-EDGE}
                                                GRAHPVIZ-SOURCE #{(node-id->iri from)}
                                                GRAHPVIZ-TARGET #{(node-id->iri to)}}}))
                           (reduce merge {}))

        ;; Combine all entities
        rdf-graph (merge node-entities edge-entities)]
    rdf-graph))

(defn transform-rdf-to-dgml-rdf
  "Transform RDF graph directly to DGML RDF representation compatible with dgml-rdf/rdf->dgml."
  [rdf-graph]
  (let [nodes (filter (fn [[entity props]]
                        (contains? (get props RDF-TYPE) GRAHPVIZ-NODE))
                      rdf-graph)
        edges (filter (fn [[entity props]]
                        (contains? (get props RDF-TYPE) GRAHPVIZ-EDGE))
                      rdf-graph)
        dgml-nodes (into {}
                         (map (fn [[entity props]]
                                (let [label (first (get props GRAHPVIZ-LABEL))]
                                  [entity (cond-> {RDF-TYPE #{DGML-NS-NODE}
                                                   ;; TODO: fix hardcoded
                                                   (dgf/dgml-iri "Name") (:local entity)}
                                            label (assoc DGML-NS-LABEL #{label}))])))
                         nodes)
        dgml-edges (into {}
                         (map (fn [[entity props]]
                                (let [source-id (first (get props GRAHPVIZ-SOURCE))
                                      target-id (first (get props GRAHPVIZ-TARGET))
                                      link-dgml-iri entity]
                                  [link-dgml-iri {RDF-TYPE #{DGML-NS-LINK}
                                                  DGML-NS-SOURCE source-id
                                                  DGML-NS-TARGET target-id}])))
                         edges)]

    (merge dgml-nodes dgml-edges)))

(comment
  (def digraph (theodora/parse (slurp "test/resources/graphviz/simple.dot")))
  (def digraph (theodora/parse (slurp "test/resources/graphviz/labels.dot")))

  (def prefix "http://example.org/mydgml/")

  (-> (graphviz->rdf {:digraph digraph :prefix prefix})

      transform-rdf-to-dgml-rdf
      (dgf/rdf->dgml {:prefix prefix}))

  (-> digraph)

  (qrdf/curie :rdf/type)
  (qrdf/curie qrdf/common-prefixes "rdf" "type")
  (qrdf/curie {"" "http://example.org/bla/"} "" "type"))
