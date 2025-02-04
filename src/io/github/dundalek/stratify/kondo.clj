(ns io.github.dundalek.stratify.kondo
  (:require
   [clj-kondo.core :as clj-kondo]))

(defn run-kondo [paths]
  (clj-kondo/run!
   {:lint paths
    :config {:output {:analysis {:keywords true}}}}))
