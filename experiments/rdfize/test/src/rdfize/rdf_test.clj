(ns src.rdfize.rdf-test
  (:require
   [clojure.test :refer [are deftest]]
   [com.xmlns.$.foaf.0%2E1 :as-alias foaf]
   [quoll.rdf :as qrdf]
   [rdfize.rdf :as rdf-utils]))

(deftest triple->primitive-test
  (are [input expected] (= expected (rdf-utils/triple->primitive input))
    (qrdf/iri "http://example.org/resource") :org.example.$/resource

    (qrdf/unsafe-blank-node "blank1") :blank1

    "literal string" "literal string"
    "" ""))

(deftest primitive->triple-test
  (are [input expected] (= expected (rdf-utils/primitive->triple input))
    :org.example.$/resource (qrdf/iri "http://example.org/resource")

    :blank1 (qrdf/unsafe-blank-node "blank1")

    "literal string" (qrdf/typed-literal "literal string")
    "" (qrdf/typed-literal "")))

(deftest triple-primitive-roundtrip-test
  (are [original] (= original (-> original rdf-utils/triple->primitive rdf-utils/primitive->triple))
    (qrdf/iri "http://example.org/resource")
    (qrdf/iri "http://subdomain.example.com/path/to/resource")
    (qrdf/unsafe-blank-node "blank1")
    (qrdf/unsafe-blank-node "blank.with.dots")
    (qrdf/typed-literal "literal string")
    (qrdf/typed-literal "")))

(deftest primitive-triple-roundtrip-test
  (are [original] (= original (-> original rdf-utils/primitive->triple rdf-utils/triple->primitive))
    :org.example.$/resource
    :com.example.subdomain.$.path.to/resource
    :blank1
    :blank%2Ewith%2Edots
    "literal string"
    ""))
