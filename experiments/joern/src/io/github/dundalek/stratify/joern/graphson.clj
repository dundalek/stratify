(ns io.github.dundalek.stratify.joern.graphson
  (:require
   [jsonista.core :as j]))

(comment
  ;; joern-export --repr all --format graphson --out graphson-go tmp/cpg-go.bin

  ;; not keywordizing because representation has things like keys like @value @type
  (def gg (j/read-value (slurp "tmp/graphson-go/export.json") j/default-object-mapper))
  (def gg (j/read-value (slurp "tmp/graphson-go/export.json") j/keyword-keys-object-mapper))

  (def at-value (keyword "@value"))
  (def at-type (keyword "@type"))

  (let [{:strs [edges vertices]} (get gg "@value")]
    [(count edges)
     (count vertices)])

  (def edges (get-in gg ["@value" "edges"]))
  (def vertices (get-in gg ["@value" "vertices"]))

  (count vertices)

  (->> edges
       (map #(get % "label"))
       frequencies
       (sort-by val)
       reverse)

  (->> vertices
       (mapcat keys)
       frequencies
       (sort-by val)
       reverse)

  (-> (group-by #(get % "label") vertices)
      (update-vals (fn [items]
                     (->> items
                          (map #(get % "properties"))
                          (mapcat keys)
                          frequencies))))

  (-> (group-by #(get % "label") edges)
      (update-vals (fn [items]
                     (->> items
                          (map #(get % "properties"))
                          (mapcat keys)
                          frequencies))))

  ;; CALL can be both edge label or vertex label
  (clojure.set/intersection
   (->> edges (map #(get % "label")) set)
   (->> vertices (map #(get % "label")) set))
   ; #{"CALL"}

  ;; node properties and edge properties do not conflict
  ;; edges only have extra "EdgeProperty" property
  (clojure.set/intersection
   (->> edges (map #(get % "properties")) (mapcat keys) set)
   (->> vertices (map #(get % "properties")) (mapcat keys) set))
  ; #{}

  (->> edges
       (map #(get % "properties"))
       (filter seq)))

