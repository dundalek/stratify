(ns io.github.dundalek.stratify.joern
  (:require
   [clojure.walk :as walk]
   [jsonista.core :as j]))

(def graphson-int64-schema
  [:map
   ["@type" [:= "g:Int64"]]
   ["@value" :int]])

(def graphson-int32-schema
  [:map
   ["@type" [:= "g:Int32"]]
   ["@value" :int]])

(def graphson-value-schema
  [:or
   graphson-int64-schema
   graphson-int32-schema
   :string
   :boolean])

(def graphson-list-schema
  [:map
   ["@type" [:= "g:List"]]
   ["@value" [:sequential graphson-value-schema]]])

(def graphson-property-schema
  [:map
   ["@type" [:= "g:Property"]]
   ["@value" :string]
   ["id" graphson-int64-schema]])

(def vertex-property-schema
  [:map
   ["@type" [:= "g:VertexProperty"]]
   ["@value" graphson-list-schema]
   ["id" graphson-int64-schema]])

(def vertex-schema
  [:map
   ["@type" [:= "g:Vertex"]]
   ["id" graphson-int64-schema]
   ["label" :string]
   ["properties" [:map-of :string vertex-property-schema]]])

(def edge-schema
  [:map
   ["@type" [:= "g:Edge"]]
   ["id" graphson-int64-schema]
   ["label" :string]
   ["inV" graphson-int64-schema]
   ["inVLabel" :string]
   ["outV" graphson-int64-schema]
   ["outVLabel" :string]
   ["properties" [:map-of :string graphson-property-schema]]])

(def joern-cpg-graphson-schema
  [:map
   ["@type" [:= "tinker:graph"]]
   ["@value" [:map
              ["edges" [:sequential edge-schema]]
              ["vertices" [:sequential vertex-schema]]]]])

(defn- extract-g-list-values
  "Extract all g:List @value contents from a Joern CPG export using walk"
  [data]
  (let [g-list-values (atom [])]
    (walk/postwalk
     (fn [node]
       (when (and (map? node) (= (get node "@type") "g:List"))
         (swap! g-list-values conj (get node "@value")))
       node)
     data)
    @g-list-values))

(defn extract-value [x]
  (if (and (map? x) (#{"g:Int64" "g:Int32"} (get x "@type")))
    (get x "@value")
    x))

(comment
  (def input-file "experiments/joern/test/resources/joern-cpg/out-go/export.json")
  (def data (j/read-value (slurp input-file) j/default-object-mapper))

  (keys data)
  (get data "@type")
  (keys (get data "@value"))

  (def vertex (first (get-in data ["@value" "vertices"])))
  (second (get-in data ["@value" "vertices"]))

  (let [{:strs [properties]} vertex]
    (keys properties))

  (let [{:strs [edges vertices]} (get data "@value")]
    (as-> {} graph
      (reduce (fn [graph vertex]
                (let [id (get-in vertex ["id" "@value"])]
                  (assoc graph id
                         (reduce (fn [m [k v]]
                                   (assoc m k (mapv extract-value (get-in v ["@value" "@value"]))))
                                 (dissoc vertex "@type" "properties")
                                 (get vertex "properties")))))
              graph
              vertices)
      (reduce (fn [graph edge]
                (let [id (get-in edge ["id" "@value"])
                      attrs (-> edge
                                (dissoc "@type" "properties")
                                (update-vals extract-value))]
                  (assoc graph id
                         (reduce (fn [m [k v]]
                                   (assoc m k (get-in v ["@value"])))
                                 attrs
                                 (get edge "properties")))))
              graph
              edges)))

  ;; Extract all g:List values
  (def g-list-values (extract-g-list-values data))
  (count g-list-values)

  (->> g-list-values
       (map count)
       (frequencies))

  (->> g-list-values
       (filter #(< 1 (count %)))))
