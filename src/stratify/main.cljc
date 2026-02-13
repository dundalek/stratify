(ns stratify.main
  (:require
   #?@(:bb [[stratify.main-bb :as main-impl]]
       :clj [[stratify.main-jvm :as main-impl]])))

(defn -main [& args]
  (apply main-impl/-main args))
