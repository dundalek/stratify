(ns org.example
  (:require
   [org.example.$ :as-alias ex]
   [com.xmlns.$.foaf.0%2E1 :as-alias foaf]
   [org.w3.www.$.1999.02.22-rdf-syntax-ns.$$ :as-alias rdf]))

(def triple [::ex/john ::rdf/type ::foaf/Person])
