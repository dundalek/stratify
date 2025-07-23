(ns io.github.dundalek.stratify.dgml-rdf-test
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.dgml-rdf :as dgf]))

(deftest merge-entity-maps-test
  (is (= {:a {:x #{1}, :y #{2}}}
         (dgf/merge-entity-maps {} {:a {:x 1 :y #{2}}})))

  (is (= {:a {:x #{1 3}, :y #{2}}
          :b {:x #{1}}}
         (dgf/merge-entity-maps {:a {:x #{1} :y #{2}}}
                                {:b {:x 1}
                                 :a {:x #{3}}}))))

(deftest canonicalize-xml-roundtrip-test
  (let [sample-dgml "test/resources/nested/output-default.dgml"
        original (xml/parse (io/reader sample-dgml))
        canonicalized (-> original
                          dgf/canonicalize-xml
                          xml/emit-str
                          xml/parse-str)]
    (is (= (dgf/canonicalize-xml original)
           canonicalized))))

(deftest rdf-dgml-roundtrip-test
  (let [sample-dgml "test/resources/nested/output-default.dgml"
        prefix "http://example.org/mydgml/"
        original (xml/parse (io/reader sample-dgml))
        rdf-graph (dgf/dgml->rdf original {:prefix prefix})
        reconstructed (dgf/rdf->dgml rdf-graph {:prefix prefix})]

    (is (= (dgf/canonicalize-xml original)
           (dgf/canonicalize-xml reconstructed)))))

(comment
  (xml/parse (io/reader "test/resources/nested/output-default.dgml")))

(deftest rdf-dgml-styles-roundtrip-test
  (let [sample-dgml "test/resources/pulumi/sample-preview-deletes.dgml"
        prefix "http://example.org/mydgml/"
        original (xml/parse (io/reader sample-dgml))
        rdf-graph (dgf/dgml->rdf original {:prefix prefix})
        reconstructed (dgf/rdf->dgml rdf-graph {:prefix prefix})]

    (is (= (dgf/canonicalize-xml original)
           (dgf/canonicalize-xml reconstructed)))))
