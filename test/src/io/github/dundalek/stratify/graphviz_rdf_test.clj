(ns io.github.dundalek.stratify.graphviz-rdf-test
  (:require
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.dgml-rdf :as dgf :refer [canonicalize-xml]]
   [io.github.dundalek.stratify.graphviz :as graphviz]
   [io.github.dundalek.stratify.graphviz-rdf :as graphviz-rdf]
   [quoll.rdf :as qrdf]
   [rdfize.turtle :as turtle]
   [theodora.core :as theodora]))

(defn absolutize-iri [x]
  (if (qrdf/iri? x)
    (str \< (:iri x) \>)
    x))

(defn absolutize-iris [value]
  (walk/prewalk absolutize-iri value))

(deftest graphviz->rdf-test
  (let [digraph (theodora/parse (slurp "test/resources/graphviz/simple.dot"))
        prefix "http://example.org/graphviz/"
        graph (-> (graphviz-rdf/graphviz->rdf {:digraph digraph
                                               :prefix prefix}))
        triples (turtle/normal-form->triples graph)]
    (is (= (absolutize-iris graph)
           (->
            (turtle/write-triples-as-string {:namespaces {"" prefix
                                                          graphviz-rdf/graphviz-ns-prefix graphviz-rdf/graphviz-ns-iri}
                                             :triples triples})
            (turtle/read-triples)
            :triples
            (turtle/triples->normal-form)
            absolutize-iris)))))

(deftest transform-rdf-to-dgml-rdf-test
  (let [input-file "test/resources/graphviz/simple.dot"
        digraph (theodora/parse (slurp input-file))
        prefix "http://example.org/graphviz/"
        graph (-> (graphviz-rdf/graphviz->rdf {:digraph digraph
                                               :prefix prefix}))
        g (graphviz/extract-graph {:input-file input-file})
        dgml (sdgml/graph->dgml g {:styles nil})]
    (is (= (canonicalize-xml dgml)
           (-> (graphviz-rdf/transform-rdf-to-dgml-rdf graph)
               (dgf/rdf->dgml {:prefix "http://example.org/graphviz/"})
               canonicalize-xml)))))

