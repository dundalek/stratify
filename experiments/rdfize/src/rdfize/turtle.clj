(ns rdfize.turtle
  (:require
   [clojure.string :as str]
   [quoll.raphael.core :as raphael]
   [quoll.rdf :as qrdf :refer [RDF-TYPE]])
  (:import
   [java.io StringWriter]))

(def RDF-VALUE (qrdf/curie qrdf/common-prefixes :rdf/value))

(def ^:private rdf-type-str (qrdf/as-str RDF-TYPE))

(defprotocol WriteValue
  (-write-value [this value]))

(extend-protocol WriteValue
  java.io.Writer
  (-write-value [this value]
    (.write this value)))

(defprotocol TurtleEncoder
  (-encode-prefix-name [this prefix])
  (-encode-prefix-ref [this prefix])
  (-encode-predicate [this prefix]))

(defprotocol EncodeValue
  (-encode-value [this value]))

(deftype DefaultTranscoder [output]
  EncodeValue
  (-encode-value [_ value]
    (cond
      ;; this is to encode iris with base created by set-local-for-iris-with-base
      (and (qrdf/iri? value) (:local value) (nil? (:prefix value)))
      (str (qrdf/iri (:local value)))

      (and (satisfies? qrdf/Node value)
           (not= (qrdf/get-type value) :unknown))
      (str value)

      :else (str (qrdf/typed-literal value))))

  TurtleEncoder
  (-encode-prefix-name [_ value]
    ;; will this need some escaping?
    (str value))
  (-encode-prefix-ref [this prefix]
    (-encode-value this (qrdf/iri prefix)))
  (-encode-predicate [this value]
    (if (and (qrdf/iri? value) (= (qrdf/as-str value) rdf-type-str))
      "a"
      (-encode-value this value)))

  WriteValue
  (-write-value [_ value]
    (-write-value output value)))

(defn default-transcoder [output]
  (->DefaultTranscoder output))

(defn write-base-and-prefixes [transcoder {:keys [base namespaces]}]
  (when base
    (-write-value transcoder (str "@base " (-encode-prefix-ref transcoder base) " .\n")))
  (when (seq namespaces)
    (doseq [[prefix namespace-iri] namespaces]
      (-write-value transcoder (str "@prefix "
                                    (-encode-prefix-name transcoder prefix) ": "
                                    (-encode-prefix-ref transcoder namespace-iri) " .\n")))
    (-write-value transcoder "\n")))

(defn- write-triple [transcoder triple]
  (let [[subject predicate object] triple
        subject-str (-encode-value transcoder subject)
        predicate-str (-encode-predicate transcoder predicate)
        object-str (-encode-value transcoder object)]
    (-write-value transcoder (str subject-str " " predicate-str " " object-str " .\n"))))

(defn- set-local-for-iris-with-base [triples base]
  (if-not base
    triples
    (letfn [(transform-iri [x]
              (if (and (qrdf/iri? x) (str/starts-with? (qrdf/as-str x) base))
                (let [iri (qrdf/as-str x)]
                  (qrdf/iri iri nil (str/replace iri base "")))
                x))]
      (mapv #(mapv transform-iri %) triples))))

(defn triples->normal-form [triples]
  (->> triples
       (reduce (fn [acc [subject predicate object]]
                 (update-in acc [subject predicate] (fnil conj #{}) object))
               {})))

(defn normal-form->triples [graph]
  (for [[s ps] graph
        [p os] ps
        o os]
    [s p o]))

(defn read-triples [in]
  (let [result (raphael/parse in)]
    (update result :triples set-local-for-iris-with-base (:base result))))

(defn- write-triples* [transcoder data]
  (write-base-and-prefixes transcoder data)
  (doseq [triple (:triples data)]
    (write-triple transcoder triple)))

(defn write-triples [out data]
  (write-triples* (default-transcoder out) data))

(defn write-triples-as-string [data]
  (let [writer (StringWriter.)]
    (write-triples writer data)
    (.toString writer)))

(declare write-node)

(defn- write-interposed [transcoder sep coll]
  (when (seq coll)
    (write-node transcoder (first coll))
    (doseq [x (rest coll)]
      (-write-value transcoder sep)
      (write-node transcoder x))))

(defn write-node [transcoder node]
  (case (::op node)
    ::terminal
    (-write-value transcoder (-encode-value transcoder (::value node)))

    ::predicate-object-list
    (let [{::keys [verb object-list]} node]
      (-write-value transcoder (-encode-predicate transcoder verb))
      (-write-value transcoder " ")
      (write-interposed transcoder " ,\n    " object-list))

    ::blank-node-property-list
    (let [{::keys [property-list]} node]
      (-write-value transcoder "[ ")
      (write-interposed transcoder " ;\n  " property-list)
      (-write-value transcoder " ]"))

    ::collection
    (let [{::keys [items]} node]
      (-write-value transcoder "( ")
      (write-interposed transcoder " " items)
      (-write-value transcoder " )"))

    ::statement
    (let [{::keys [subject predicate-object-lists]} node]
      (write-node transcoder subject)
      (when (seq predicate-object-lists)
        (-write-value transcoder " ")
        (write-interposed transcoder " ;\n  " predicate-object-lists))
      (-write-value transcoder " .\n"))))

(defn node-terminal [item]
  {::op ::terminal ::value item})

(defn node-predicate-object-list [verb objects]
  {::op ::predicate-object-list ::verb verb ::object-list objects})

(defn node-blank-with-value [value-content]
  {::op ::blank-node-property-list
   ::property-list [(node-predicate-object-list RDF-VALUE [value-content])]})

(defn node-blank-with-type [type-iri value-content]
  {::op ::blank-node-property-list
   ::property-list [(node-predicate-object-list RDF-TYPE [(node-terminal type-iri)])
                    (node-predicate-object-list RDF-VALUE [value-content])]})

(defn node-collection [items]
  {::op ::collection ::items items})

(defn node-statement [subject]
  {::op ::statement ::subject subject})
