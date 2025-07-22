(ns io.github.dundalek.stratify.dgml-rdf
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [quoll.rdf :as qrdf :refer [RDF-TYPE]]
   [rdfize.turtle :as turtle]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias xdgml])
  (:import
   [java.net URLEncoder]
   [java.nio.charset StandardCharsets]))

(defn merge-entity-maps [target source]
  (reduce-kv
   (fn [m ek ev]
     (reduce-kv
      (fn [m ak av]
        (update-in m [ek ak]
                   (fn [v]
                     (let [result (or v #{})]
                       (if (set? av)
                         (into result av)
                         (conj result av))))))
      m
      ev))
   target
   source))

(def dgml-ns-iri "http://dundalek.github.io/stratify/dgml-ns/")
(def dgml-ns-prefix "dgml-ns")

(defn dgml-iri [local]
  (qrdf/iri (str dgml-ns-iri local) dgml-ns-prefix local))

(def DGML-NS-NODE (dgml-iri "Node"))
(def DGML-NS-LINK (dgml-iri "Link"))
(def DGML-NS-STYLE (dgml-iri "Style"))
(def DGML-NS-CONDITION (dgml-iri "Condition"))
(def DGML-NS-SETTER (dgml-iri "Setter"))
(def DGML-NS-HAS-CHILD (dgml-iri "hasChild"))

(defn normalize-attrs
  "Converts attribute maps by taking the first value from sets."
  [attrs]
  (->> attrs
       (map (fn [[k v]]
              [k (if (set? v) (first v) v)]))
       (into {})))

(defn canonicalize-xml [element]
  (walk/postwalk
   (fn [item]
     (if (and (map? item) (contains? item :content))
       (update item :content
               (fn [content]
                 (->> content
                      (remove #(and (string? %) (str/blank? %)))
                      (sort-by hash)
                      vec)))
       item))
   element))

(defn dgml->rdf [data]
  (let [!id-generator (atom 0)
        generate-id! #(qrdf/unsafe-blank-node (str "b" (swap! !id-generator inc)))
        prefix "http://example.org/mydgml/"
        node-id->iri (fn [id]
                       (let [encoded (URLEncoder/encode id StandardCharsets/UTF_8)]
                         (qrdf/iri (str prefix encoded) "" encoded)))
        links-element (->> (:content data)
                           (filter (comp #{::xdgml/Links} :tag))
                           (first))
        links (->> (:content links-element)
                   (filter (comp #{::xdgml/Link} :tag))
                   (map (fn [{:keys [attrs]}]
                          {(generate-id!) (-> attrs
                                              (update :Source node-id->iri)
                                              (update :Target node-id->iri)
                                              (assoc RDF-TYPE DGML-NS-LINK))})))
        nodes-element (->> (:content data)
                           (filter (comp #{::xdgml/Nodes} :tag))
                           (first))
        nodes (->> (:content nodes-element)
                   (filter (comp #{::xdgml/Node} :tag))
                   (map (fn [{:keys [attrs]}]
                          {(node-id->iri (:Id attrs))
                           (-> attrs
                               (dissoc :Id)
                               (assoc RDF-TYPE DGML-NS-NODE))})))
        styles-element (->> (:content data)
                            (filter (comp #{::xdgml/Styles} :tag))
                            (first))
        styles (when styles-element
                 (->> (:content styles-element)
                      (filter (comp #{::xdgml/Style} :tag))
                      (map (fn [{:keys [attrs content]}]
                             (let [style-id (generate-id!)
                                   children (->> content
                                                 (filter :tag)
                                                 (map (fn [{:keys [tag attrs]}]
                                                        (let [child-id (generate-id!)
                                                              child-type (case tag
                                                                           ::xdgml/Condition DGML-NS-CONDITION
                                                                           ::xdgml/Setter DGML-NS-SETTER)]
                                                          {child-id (-> attrs
                                                                        (assoc RDF-TYPE child-type))
                                                           style-id {DGML-NS-HAS-CHILD child-id}})))
                                                 (reduce merge-entity-maps {}))]
                               (merge-entity-maps
                                {style-id (-> attrs
                                              (assoc RDF-TYPE DGML-NS-STYLE))}
                                children))))))]
    (as-> {} graph
      (reduce merge-entity-maps graph nodes)
      (reduce merge-entity-maps graph links)
      (reduce merge-entity-maps graph styles))))

(defn rdf->dgml [rdf-graph]
  (let [prefix "http://example.org/mydgml/"
        iri->node-id (fn [iri]
                       (if (qrdf/iri? iri)
                         (let [iri-str (qrdf/as-str iri)]
                           (when (str/starts-with? iri-str prefix)
                             (java.net.URLDecoder/decode
                              (subs iri-str (count prefix))
                              java.nio.charset.StandardCharsets/UTF_8)))
                         nil))

        nodes (for [[entity attrs] rdf-graph
                    :when (contains? (get attrs RDF-TYPE #{}) DGML-NS-NODE)
                    :let [node-id (iri->node-id entity)]
                    :when node-id]
                {:tag ::xdgml/Node
                 :attrs (-> (dissoc attrs RDF-TYPE)
                            (assoc :Id node-id)
                            normalize-attrs)
                 :content []})

        links (for [[entity attrs] rdf-graph
                    :when (contains? (get attrs RDF-TYPE #{}) DGML-NS-LINK)
                    :let [source-iri (first (:Source attrs))
                          target-iri (first (:Target attrs))
                          source-id (iri->node-id source-iri)
                          target-id (iri->node-id target-iri)]
                    :when (and source-id target-id)]
                {:tag ::xdgml/Link
                 :attrs (-> (dissoc attrs RDF-TYPE :Source :Target)
                            (assoc :Source source-id :Target target-id)
                            normalize-attrs)
                 :content []})

        styles (for [[entity attrs] rdf-graph
                     :when (contains? (get attrs RDF-TYPE #{}) DGML-NS-STYLE)]
                 (let [child-entities (get attrs DGML-NS-HAS-CHILD #{})
                       children (for [child-entity child-entities
                                      :let [child-attrs (get rdf-graph child-entity)]
                                      :when child-attrs]
                                  (let [child-type (first (get child-attrs RDF-TYPE #{}))
                                        tag (cond
                                              (or (= child-type DGML-NS-CONDITION)
                                                  (= child-type :dgml-ns/Condition)) ::xdgml/Condition
                                              (or (= child-type DGML-NS-SETTER)
                                                  (= child-type :dgml-ns/Setter)) ::xdgml/Setter
                                              :else (throw (ex-info "Unknown child type" {:child-type child-type})))]
                                    {:tag tag
                                     :attrs (->> (dissoc child-attrs RDF-TYPE)
                                                 normalize-attrs)
                                     :content []}))]
                   {:tag ::xdgml/Style
                    :attrs (->> (dissoc attrs RDF-TYPE DGML-NS-HAS-CHILD)
                                normalize-attrs)
                    :content children}))]

    {:tag ::xdgml/DirectedGraph
     :attrs {}
     :content (cond-> []
                (seq links)
                (conj {:tag ::xdgml/Links
                       :attrs {}
                       :content links})
                (seq nodes)
                (conj {:tag ::xdgml/Nodes
                       :attrs {}
                       :content nodes})
                (seq styles)
                (conj {:tag ::xdgml/Styles
                       :attrs {}
                       :content styles}))}))

(comment
  (def input-file "test/resources/nested/output-default.dgml")
  (def data (xml/parse (io/reader input-file)))

  (canonicalize-xml data)

  (let [prefix "http://example.org/mydgml/"
        triples (->> (dgml->rdf data)
                     (turtle/normal-form->triples)
                     (mapv (fn [triple]
                             (update triple 1
                                     (fn [kw]
                                       (if (keyword? kw)
                                         (qrdf/iri (str prefix (name kw)) "" (name kw))
                                         kw))))))]

    (with-open [w (io/writer "dgml.ttl")]
      (turtle/write-triples w {:namespaces {"rdf" (:rdf qrdf/common-prefixes)
                                            dgml-ns-prefix dgml-ns-iri
                                            "" prefix}
                               :triples triples})))

  (turtle/read-triples (io/reader "dgml.ttl")))
