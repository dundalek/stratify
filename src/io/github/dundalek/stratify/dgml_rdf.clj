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

(defn kw->dgml-iri [kw]
  (dgml-iri (name kw)))

(def DGML-NS-NODE (dgml-iri "Node"))
(def DGML-NS-LINK (dgml-iri "Link"))
(def DGML-NS-STYLE (dgml-iri "Style"))
(def DGML-NS-CONDITION (dgml-iri "Condition"))
(def DGML-NS-SETTER (dgml-iri "Setter"))
(def DGML-NS-HAS-CHILD (dgml-iri "hasChild"))
(def DGML-NS-SOURCE (dgml-iri "Source"))
(def DGML-NS-TARGET (dgml-iri "Target"))
(def DGML-NS-LABEL (dgml-iri "Label"))

(defn normalize-attrs
  "Converts attribute maps by taking the first value from sets."
  [attrs]
  (->> attrs
       (map (fn [[k v]]
              (let [x (if (set? v) (first v) v)
                    ;; should probably check or filter attrs to make sure they are in dgml-ns
                    k (if (qrdf/iri? k) (keyword (:local k)) k)]
                [k x])))
       (into {})))

(defn canonicalize-xml [element]
  (walk/postwalk
   (fn [item]
     (cond-> item
       (and (map? item) (contains? item :content))
       (update :content
               (fn [content]
                 (->> content
                      flatten
                      (remove #(and (string? %) (str/blank? %)))
                      (sort-by hash)
                      vec)))

       ;; it seems that xml parser strips it, so we strip it for comparison
       (and (map? item) (= (:tag item) ::xdgml/DirectedGraph))
       (update :attrs dissoc :xmlns)))

   element))

(defn styles->rdf [styles {:keys [generate-id!]}]
  (->> styles
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
                                                         (update-keys kw->dgml-iri)
                                                         (assoc RDF-TYPE child-type))
                                            style-id {DGML-NS-HAS-CHILD child-id}})))
                                  (reduce merge-entity-maps {}))]
                (merge-entity-maps
                 {style-id (-> attrs
                               (update-keys kw->dgml-iri)
                               (assoc RDF-TYPE DGML-NS-STYLE))}
                 children))))))

(defn dgml->rdf [data {:keys [prefix]}]
  (let [!id-generator (atom 0)
        generate-id! #(qrdf/unsafe-blank-node (str "b" (swap! !id-generator inc)))
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
                                              (update-keys kw->dgml-iri)
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
                               (update-keys kw->dgml-iri)
                               (assoc RDF-TYPE DGML-NS-NODE))})))
        styles-element (->> (:content data)
                            (filter (comp #{::xdgml/Styles} :tag))
                            (first))
        styles (when styles-element
                 (styles->rdf (->> (:content styles-element)
                                   (filter (comp #{::xdgml/Style} :tag)))
                              {:generate-id! generate-id!}))]
    (as-> {} graph
      (reduce merge-entity-maps graph nodes)
      (reduce merge-entity-maps graph links)
      (reduce merge-entity-maps graph styles))))

(defn rdf->dgml [rdf-graph {:keys [prefix]}]
  (let [iri->node-id (fn [iri]
                       #_(qrdf/as-str iri)
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
                            normalize-attrs
                            (assoc :Id node-id))
                 :content []})

        links (for [[entity attrs] rdf-graph
                    :when (contains? (get attrs RDF-TYPE #{}) DGML-NS-LINK)
                    :let [attrs (-> attrs
                                    (dissoc attrs RDF-TYPE)
                                    normalize-attrs)
                          source-iri (:Source attrs)
                          target-iri (:Target attrs)
                          source-id (iri->node-id source-iri)
                          target-id (iri->node-id target-iri)]]
                {:tag ::xdgml/Link
                 :attrs (-> attrs
                            (assoc :Source source-id :Target target-id))
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
     :attrs {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
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

  (->> (dgml->rdf data)
       (rdf->dgml))

  (qrdf/iri? (key (first xxx)))
  (normalize-attrs xxx)

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
