(ns rdfize.rdf
  (:require
   [clojure.string :as str]
   [quoll.rdf :as qrdf]
   [rdfize.qni :as qni])
  (:import
   [java.net URLDecoder URLEncoder]
   [java.nio.charset StandardCharsets]))

(defn triple->primitive [triple]
  (cond
    (qrdf/iri? triple) (qni/uri->namespaced-kw (:iri triple))
    (qrdf/blank? triple) (-> (:id triple)
                             (URLEncoder/encode StandardCharsets/UTF_8)
                             (str/replace "." "%2E")
                             keyword)
    (qrdf/typed-literal? triple) (qrdf/to-clj triple)
    :else triple))

(defn primitive->triple
  ([primitive] (primitive->triple primitive {}))
  ([primitive options]
   (let [{:keys [namespaces]} options]
     (cond
       (and (keyword? primitive) (namespace primitive))
       (let [iri (qni/namespaced-kw->uri primitive)]
         (if-some [[prefix local] (some (fn [[prefix namespace-uri]]
                                          (when (str/starts-with? iri namespace-uri)
                                            [prefix (subs iri (count namespace-uri))]))
                                        namespaces)]
           (qrdf/iri iri prefix local)
           (qrdf/iri iri)))

       (keyword? primitive)
       (qrdf/unsafe-blank-node
        (-> (name primitive)
            (URLDecoder/decode StandardCharsets/UTF_8)
            (str/replace "%2E" ".")))

       (number? primitive) primitive
       (boolean? primitive) primitive
       :else (qrdf/typed-literal primitive)))))
