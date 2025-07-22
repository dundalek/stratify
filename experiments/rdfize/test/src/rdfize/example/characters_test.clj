(ns rdfize.example.characters-test
  (:require
   [cheshire.core :as cheshire]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [com.xmlns.$.foaf.0%2E1 :as-alias foaf]
   [rdfize.example.characters :refer [characters->triples]]
   [rdfize.turtle :as turtle]))

(deftest transform-characters-test
  (let [triples (characters->triples (cheshire/parse-stream (io/reader "test/resources/rml-characters.json") true))]
    (is (= (-> (turtle/read-triples (io/reader "test/resources/rml-characters.ttl"))
               :triples
               (turtle/triples->normal-form))
           (turtle/triples->normal-form triples)))))
