(ns ^:focus io.github.dundalek.stratify.dgml-rdf-test
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.dgml-rdf :as dgf]))

(deftest foo
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
        original (xml/parse (io/reader sample-dgml))
        rdf-graph (dgf/dgml->rdf original)
        reconstructed (dgf/rdf->dgml rdf-graph)]

    (is (= (dgf/canonicalize-xml original)
           (dgf/canonicalize-xml reconstructed)))))

(deftest rdf-dgml-styles-roundtrip-test
  (let [sample-dgml "test/resources/pulumi/sample-preview-deletes.dgml"
        original (xml/parse (io/reader sample-dgml))
        rdf-graph (dgf/dgml->rdf original)
        reconstructed (dgf/rdf->dgml rdf-graph)]

    (is (= (dgf/canonicalize-xml original)
           (dgf/canonicalize-xml reconstructed)))))
