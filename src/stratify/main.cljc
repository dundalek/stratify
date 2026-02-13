(ns stratify.main
  (:require [stratify.main-jvm :as main-jvm]))

(defn -main [& args]
  (apply main-jvm/-main args))
