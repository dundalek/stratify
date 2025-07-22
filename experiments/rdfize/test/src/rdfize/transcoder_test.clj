(ns src.rdfize.transcoder-test
  (:require
   [clojure.test :refer [are deftest is]]
   [rdfize.transcoder :as transcoder]
   [rdfize.turtle :as turtle]))

(def primitive-edn-values
  [42
   3.14
   "abc"
   true
   false
   nil
   :ns/kw
   :kw
   'abc
   'ns/abc])

(def collection-edn-values
  [[1 2 3]
   {:a 1}
   '(1 2 3)
   #{1 2 3}
   {:a [1 2 3]
    "b" ["a" "b" "c"]}
   []
   {}
   #{}

   {:name "John"
    :age 30
    :isStudent true
    :address nil
    :symb 'some-symbol
    :tags #{:clojure :rdf}
    :scores [95 87 92]}])
   ; turtle treats empty list as nil
   ; '()])

(def json-roundtrip-values
  [42
   3.14
   "abc"
   true
   false
   nil
   [1 2 3]
   {"a" 1}])

(def json-encode-test-cases
  [["kw" :kw]
   ["abc" 'abc]
   [[1 2 3] '(1 2 3)]
   [{"a" 1} {:a 1}]
   ["abc" 'ns/abc]
   ["kw" :ns/kw]])

(def complex-json-test-data
  {:a [1 2 3]
   "b" ["a" "b" "c"]})

(deftest edn-roundrip-test
  (doseq [value primitive-edn-values]
    (is (= value (transcoder/read-edn (transcoder/write-edn-as-string value)))))
  (doseq [value collection-edn-values]
    (is (= value (transcoder/read-edn (transcoder/write-edn-as-string value))))))

(deftest json-roundrip-test
  (doseq [value json-roundtrip-values]
    (is (= value (transcoder/read-json (transcoder/write-json-as-string value))))))

(deftest json-encode-test
  (doseq [[expected value] json-encode-test-cases]
    (is (= expected (transcoder/read-json (transcoder/write-json-as-string value)))))

  (is (= {"a" [1 2 3]
          "b" ["a" "b" "c"]}
         (transcoder/read-json (transcoder/write-json-as-string complex-json-test-data)))))

(deftest compact-edn-roundrip-test
  (doseq [value primitive-edn-values]
    (is (= value (transcoder/read-edn (transcoder/write-edn-compact-as-string value)))))
  (doseq [value collection-edn-values]
    (is (= value (transcoder/read-edn (transcoder/write-edn-compact-as-string value))))))

(deftest compact-json-roundtrip-test
  (doseq [value json-roundtrip-values]
    (is (= value (transcoder/read-json (transcoder/write-json-compact-as-string value))))))

(deftest compact-json-encode-test
  (doseq [[expected value] json-encode-test-cases]
    (is (= expected (transcoder/read-json (transcoder/write-json-compact-as-string value)))))

  (is (= {"a" [1 2 3]
          "b" ["a" "b" "c"]}
         (transcoder/read-json (transcoder/write-json-compact-as-string complex-json-test-data)))))

(deftest compact-json-encode-test-symbols-keywords-test
  (is (= (transcoder/write-json-compact-as-string "abc")
         (transcoder/write-json-compact-as-string 'abc)))
  (is (= (transcoder/write-json-compact-as-string "abc")
         (transcoder/write-json-compact-as-string :abc))))

(deftest compact-turtle-serialization
  (is (= "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix clj: <http://example.org/clojure/lang/> .

( \"a\" \"b\" \"c\" ) .
"
         (transcoder/write-edn-compact-as-string (list "a" "b" "c"))))
  (is (= "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix clj: <http://example.org/clojure/lang/> .

[ a clj:PersistentVector ;
  rdf:value ( \"a\" \"b\" \"c\" ) ] .
"
         (transcoder/write-edn-compact-as-string ["a" "b" "c"]))))

(comment
  (println (transcoder/write-json-as-string ["a" "b"]))
  (println (transcoder/write-edn-as-string (list "a" "b" "c")))

  (transcoder/read-edn (transcoder/write-value-compact-as-string [1 2 3] {}))

  (spit "out/deps.ttl" (transcoder/write-value-compact-as-string (clojure.edn/read-string (slurp "deps.edn")) {}))

  (spit "out/deps.ttl" (transcoder/write-value-compact-as-string (clojure.edn/read-string (slurp "deps.edn")) {}))

  (-> (transcoder/write-json-compact-as-string 'abc)
      (transcoder/read-json))

  (-> (transcoder/write-json-compact-as-string 'abc)

      turtle/read-triples
      :triples
      turtle/triples->normal-form))
