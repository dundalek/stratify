(ns io.github.dundalek.stratify.loom
  (:require
   [loom.attr :as la]))

(defn add-attrs [g node-or-edge attrs]
  (reduce (fn [g [k v]]
            (la/add-attr g node-or-edge k v))
          g attrs))
