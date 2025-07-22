(ns rdfize.example.ocif-test
  (:require
   [cheshire.core :as cheshire]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [rdfize.example.ocif :as ocif]
   [rdfize.turtle :as turtle]))

(defn normalize-ocif
  "Normalizes OCIF data by sorting nodes, relations, and resources by :id for deterministic comparison."
  [ocif-data]
  (-> ocif-data
      (update :nodes #(sort-by :id %))
      (update :relations #(sort-by :id %))
      (update :resources #(sort-by :id %))))

(deftest ocif-round-trip-test
  (testing "OCIF data converts to RDF triples and back without information loss"
    (let [hello-data (cheshire/parse-stream (io/reader "test/resources/ocif-hello.json") true)
          triples (ocif/data->triples hello-data "http://example.com/ocif/")
          graph (turtle/triples->normal-form triples)
          reconstructed-data (ocif/rdf->data graph "http://example.com/ocif/")]
      (is (= (normalize-ocif hello-data) (normalize-ocif reconstructed-data))))))
