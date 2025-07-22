(ns rdfize.example.characters
  (:require
   [quoll.rdf :as qrdf]
   [rdfize.qni :as qni]))

(defn characters->triples [data]
  (->> data
       :characters
       (mapcat (fn [character]
                 (let [id (keyword "org.example.$.character" (str "_" (:id character)))]
                   (cond-> [[id :org.w3.www.$.1999.02.22-rdf-syntax-ns.$$/type :org.schema.$/Person]
                            [id :org.schema.$/givenName (:firstname character)]
                            [id :org.dbpedia.$.ontology/hairColor (:hair character)]]
                     (:lastname character)
                     (conj [id :org.schema.$/lastName (:lastname character)])))))
       (map (fn [triple]
              (mapv
               (fn [value]
                 (if (keyword? value)
                   (qrdf/iri (qni/namespaced-kw->uri value))
                   value))
               triple)))))


