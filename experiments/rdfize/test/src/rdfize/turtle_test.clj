(ns rdfize.turtle-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [quoll.rdf :as qrdf]
   [rdfize.turtle :as turtle]))

(defn test-triples-roundtrip [file-path]
  (testing (str file-path)
    (let [triples (turtle/read-triples (io/reader file-path))]
      (is (= triples (turtle/read-triples (turtle/write-triples-as-string triples)))))))

(defn test-triples-full-roundtrip [file-path]
  (testing (str file-path)
    (let [triples (turtle/read-triples (io/reader file-path))
          content (turtle/write-triples-as-string triples)]
      (is (= (slurp file-path) content))
      (is (= triples (turtle/read-triples content))))))

(deftest triples-roundrip-test
  (test-triples-roundtrip "test/resources/example.ttl")
  (test-triples-roundtrip "test/resources/rml-characters.ttl")

  (test-triples-roundtrip "test/resources/blanks.ttl")
  (test-triples-roundtrip "test/resources/json-data.ttl")

  ;; would need some canonical writer to keep blanknode ids
  ; (test-triples-roundtrip "test/resources/blanks-nested.ttl")
  ; (test-triples-roundtrip "test/resources/empty-prefix.ttl")
  ; (test-triples-roundtrip "test/resources/unlabeled-blank-node-subject.ttl")

  (test-triples-full-roundtrip "test/resources/hello.ttl"))

(defn test-graph-roundrip [file-path]
  (let [{:keys [base namespaces triples]} (turtle/read-triples (io/reader file-path))
        graph (turtle/triples->normal-form triples)]
    (is (= graph
           (-> (turtle/write-triples-as-string {:base base
                                                :namespaces namespaces
                                                :triples (turtle/normal-form->triples graph)})
               turtle/read-triples
               :triples
               turtle/triples->normal-form)))))

(deftest triples-graph-roundrip-test
  (test-graph-roundrip "test/resources/example.ttl")
  (test-triples-roundtrip "test/resources/rml-characters.ttl")

  (test-triples-roundtrip "test/resources/blanks.ttl")
  (test-triples-roundtrip "test/resources/json-data.ttl"))

  ; (test-triples-roundtrip "test/resources/blanks-nested.ttl"))
  ; (test-triples-roundtrip "test/resources/empty-prefix.ttl")
  ; (test-triples-roundtrip "test/resources/unlabeled-blank-node-subject.ttl")

(deftest namespace-resolution
  (testing "namespaces expressed in different ways get resolved as same uri"
    (is (= {(qrdf/iri "http://example.org/product1")
            {(qrdf/iri "http://example.org/tag")
             #{"a" "b" "c" "d"}}}
           (-> (turtle/read-triples "
@base <http://example.org/> .
@prefix : <http://example.org/> .
@prefix ex: <http://example.org/> .

<product1> <tag> \"a\".
:product1 <tag> \"b\".
ex:product1 <tag> \"c\".
<http://example.org/product1> <tag> \"d\".
")
               :triples
               (turtle/triples->normal-form))))))
