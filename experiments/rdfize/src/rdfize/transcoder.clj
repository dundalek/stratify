(ns rdfize.transcoder
  (:require
   [clojure.edn :as edn]
   [quoll.raphael.core :as raphael]
   [quoll.rdf :as qrdf :refer [RDF-FIRST RDF-NIL RDF-REST RDF-TYPE]]
   [rdfize.turtle :as turtle :refer [RDF-VALUE]])
  (:import
   [java.io StringWriter]))

(def clj-prefix "http://example.org/clojure/lang/")

(defn clj-iri [local]
  (qrdf/iri (str clj-prefix local) "clj" local))

;; maybe we should have these under our namespace?
(def CLJ-KEYWORD (clj-iri "Keyword"))
(def CLJ-SYMBOL (clj-iri "Symbol"))
(def CLJ-VECTOR (clj-iri "PersistentVector"))
(def CLJ-MAP (clj-iri "PersistentMap")) ;; this does not exist, because there is PersistentArrayMap, PeristentHashMap
(def CLJ-LIST (clj-iri "PersistentList"))
(def CLJ-SET (clj-iri "PersistentSet")) ;; this does not exist, because there is PersistentHashSet

(defprotocol NodeGenerator
  (-new-node! [this]))

(defprotocol DecodeGraphValue
  (-decode-graph-value [this graph value]))

(defn decode-sequence [transcoder graph value]
  (when (and value (not= value RDF-NIL))
    (cons
     (-decode-graph-value transcoder graph (first (get-in graph [value RDF-FIRST])))
     (lazy-seq (decode-sequence transcoder graph (first (get-in graph [value RDF-REST])))))))

(defn edn-decode-literal [value]
  (cond
    (= RDF-NIL value) nil

    ;; different from XSD-QNAME to allow unqualified keywords
    (and (qrdf/literal? value) (= (:datatype value) CLJ-KEYWORD))
    (let [result (edn/read-string (:value value))]
      (assert (keyword? result))
      result)

    (and (qrdf/literal? value) (= (:datatype value) CLJ-SYMBOL))
    (let [result (edn/read-string (:value value))]
      (assert (symbol? result))
      result)

    (qrdf/literal? value) (qrdf/to-clj value)

    :else value))

(defn edn-decode-value [transcoder graph id]
  (let [ty (first (get-in graph [id RDF-TYPE]))]
    (cond
      (= ty CLJ-VECTOR) (vec (decode-sequence transcoder graph (first (get-in graph [id RDF-VALUE]))))
      (= ty CLJ-LIST) (decode-sequence transcoder graph (first (get-in graph [id RDF-VALUE])))
      (= ty CLJ-SET) (set (decode-sequence transcoder graph (first (get-in graph [id RDF-VALUE]))))
      (= ty CLJ-MAP) (->> (decode-sequence transcoder graph (first (get-in graph [id RDF-VALUE])))
                          (partition 2)
                          (map vec)
                          (into {}))
      (first (get-in graph [id RDF-FIRST])) (decode-sequence transcoder graph id)

      :else (if-some [value (first (get-in graph [id RDF-VALUE]))]
              (edn-decode-value transcoder graph value)
              (edn-decode-literal id)))))

(defn edn-encode-literal [value]
  (cond
    (nil? value) RDF-NIL
    (keyword? value) (qrdf/typed-literal value CLJ-KEYWORD)
    (symbol? value) (qrdf/typed-literal value CLJ-SYMBOL)
    (or (number? value) (boolean? value) (string? value)) value))

(defn encode-sequence [transcoder value]
  (if-some [[x & xs] (seq value)]
    (let [id (-new-node! transcoder)
          encoded-x (turtle/-encode-value transcoder x)
          encoded-xs (encode-sequence transcoder xs)]
      {:object id :triples (concat
                            [[id RDF-FIRST (:object encoded-x)]
                             [id RDF-REST (:object encoded-xs)]]
                            (:triples encoded-x)
                            (:triples encoded-xs))})
    {:object RDF-NIL :triples []}))

(defn- encode-collection-with-type [transcoder node type-iri sequence-data]
  (let [{:keys [object triples]} (encode-sequence transcoder sequence-data)]
    (concat [[node RDF-TYPE type-iri]
             [node RDF-VALUE object]]
            triples)))

(defn edn-encode-value [transcoder value]
  (let [node (-new-node! transcoder)
        triples (cond
                  (vector? value) (encode-collection-with-type transcoder node CLJ-VECTOR value)
                  (map? value) (encode-collection-with-type transcoder node CLJ-MAP (mapcat identity value))
                  (set? value) (encode-collection-with-type transcoder node CLJ-SET value)
                  (seq? value) (encode-collection-with-type transcoder node CLJ-LIST value)
                  :else [[node RDF-VALUE (edn-encode-literal value)]])]
    {:object node :triples triples}))

(deftype EdnTranscoder [^:volatile-mutable generator]
  turtle/EncodeValue
  (turtle/-encode-value [this value]
    (edn-encode-value this value))

  DecodeGraphValue
  (-decode-graph-value [this graph value]
    (edn-decode-value this graph value))

  NodeGenerator
  (-new-node! [_]
    (let [[new-generator node] (raphael/new-node generator)]
      (set! generator new-generator)
      node)))

(defn edn-transcoder []
  (->EdnTranscoder (raphael/new-generator)))

(defn json-decode-literal [value]
  (cond
    (= RDF-NIL value) nil

    (qrdf/literal? value) (qrdf/to-clj value)

    :else value))

(defn json-decode-value [transcoder graph id]
  (let [ty (first (get-in graph [id RDF-TYPE]))]
    (cond
      (= ty CLJ-VECTOR) (vec (decode-sequence transcoder graph (first (get-in graph [id RDF-VALUE]))))
      (= ty CLJ-MAP) (->> (decode-sequence transcoder graph (first (get-in graph [id RDF-VALUE])))
                          (partition 2)
                          (map vec)
                          (into {}))
      (first (get-in graph [id RDF-FIRST])) (vec (decode-sequence transcoder graph id))

      :else (if-some [value (first (get-in graph [id RDF-VALUE]))]
              (json-decode-value transcoder graph value)
              (json-decode-literal id)))))

(defn json-encode-literal [value]
  (cond
    (nil? value) RDF-NIL
    (keyword? value) (name value)
    (symbol? value) (name value)
    (or (number? value) (boolean? value) (string? value)) value))

(defn json-encode-value [transcoder value]
  (let [node (-new-node! transcoder)
        triples (cond
                  (map? value) (encode-collection-with-type transcoder node CLJ-MAP (mapcat identity value))
                  (or (vector? value) (seq? value) (set? value)) (encode-collection-with-type transcoder node CLJ-VECTOR value)
                  :else [[node RDF-VALUE (json-encode-literal value)]])]
    {:object node :triples triples}))

(deftype JsonTranscoder [^:volatile-mutable generator]
  turtle/EncodeValue
  (turtle/-encode-value [this value]
    (json-encode-value this value))

  DecodeGraphValue
  (-decode-graph-value [this graph value]
    (json-decode-value this graph value))

  NodeGenerator
  (-new-node! [_]
    (let [[new-generator node] (raphael/new-node generator)]
      (set! generator new-generator)
      node)))

(defn json-transcoder []
  (->JsonTranscoder (raphael/new-generator)))

(defn read-value [in transcoder]
  (let [result (raphael/parse in)
        graph (turtle/triples->normal-form (:triples result))
        root (qrdf/unsafe-blank-node "b0")]
    (-decode-graph-value transcoder graph root)))

(defn write-value [out data transcoder]
  (let [triples (:triples (turtle/-encode-value transcoder data))]
    (turtle/write-triples out {:namespaces {"rdf" (:rdf qrdf/common-prefixes)
                                            "xsd" (:xsd qrdf/common-prefixes)
                                            "clj" clj-prefix}
                               :triples triples})))

(defn write-value-as-string [data transcoder]
  (let [writer (StringWriter.)]
    (write-value writer data transcoder)
    (.toString writer)))

(defn read-edn [in]
  (read-value in (edn-transcoder)))

(defn write-edn [out data]
  (write-value out data (edn-transcoder)))

(defn write-edn-as-string [data]
  (write-value-as-string data (edn-transcoder)))

(defn read-json [in]
  (read-value in (json-transcoder)))

(defn write-json [out data]
  (write-value out data (json-transcoder)))

(defn write-json-as-string [data]
  (write-value-as-string data (json-transcoder)))

(defn- compact-encode-collection [transcoder items]
  (turtle/node-collection (mapv #(turtle/-encode-value transcoder %) items)))

(defn- compact-edn-encode-value [transcoder value]
  (cond
    (vector? value)
    (turtle/node-blank-with-type CLJ-VECTOR (compact-encode-collection transcoder value))

    (map? value)
    (turtle/node-blank-with-type CLJ-MAP (compact-encode-collection transcoder (mapcat identity value)))

    (set? value)
    (turtle/node-blank-with-type CLJ-SET (compact-encode-collection transcoder value))

    (seq? value)
    (compact-encode-collection transcoder value)

    :else
    (turtle/node-terminal (edn-encode-literal value))))

(deftype CompactEdnTranscoder [^:volatile-mutable generator]
  turtle/EncodeValue
  (turtle/-encode-value [this value]
    (compact-edn-encode-value this value)))

(defn compact-edn-transcoder []
  (->CompactEdnTranscoder (raphael/new-generator)))

(defn write-value-compact [out data transcoder]
  (let [turtle-transcoder (turtle/default-transcoder out)
        encoded-value (turtle/-encode-value transcoder data)
        root-node (turtle/node-statement
                   (if (= (::turtle/op encoded-value) ::turtle/terminal)
                     (turtle/node-blank-with-value encoded-value)
                     encoded-value))]
    (turtle/write-base-and-prefixes turtle-transcoder
                                    {:namespaces {"rdf" (:rdf qrdf/common-prefixes)
                                                  "xsd" (:xsd qrdf/common-prefixes)
                                                  "clj" clj-prefix}})
    (turtle/write-node turtle-transcoder root-node)))

(defn write-value-compact-as-string [data transcoder]
  (let [writer (StringWriter.)]
    (write-value-compact writer data transcoder)
    (.toString writer)))

(defn write-edn-compact [out data]
  (write-value-compact out data (compact-edn-transcoder)))

(defn write-edn-compact-as-string [data]
  (write-value-compact-as-string data (compact-edn-transcoder)))

(defn- compact-json-encode-value [transcoder value]
  (cond
    (map? value)
    (turtle/node-blank-with-type CLJ-MAP (compact-encode-collection transcoder (mapcat identity value)))

    (or (vector? value) (seq? value) (set? value))
    (compact-encode-collection transcoder value)

    :else (turtle/node-terminal (json-encode-literal value))))

(deftype CompactJsonTranscoder [^:volatile-mutable generator]
  turtle/EncodeValue
  (turtle/-encode-value [this value]
    (compact-json-encode-value this value))

  DecodeGraphValue
  (-decode-graph-value [this graph value]
    (json-decode-value this graph value)))

(defn compact-json-transcoder []
  (->CompactJsonTranscoder (raphael/new-generator)))

(defn write-json-compact [out data]
  (write-value-compact out data (compact-json-transcoder)))

(defn write-json-compact-as-string [data]
  (write-value-compact-as-string data (compact-json-transcoder)))
