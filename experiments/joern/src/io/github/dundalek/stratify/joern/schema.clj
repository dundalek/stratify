(ns io.github.dundalek.stratify.joern.schema
  (:require
   [clojure.data.xml :as xml]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.style :as style]
   [jsonista.core :as j]
   [malli.core :as m]
   [malli.transform :as mt]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

(def Layer
  [:map
   [:name :string]
   [:description :string]
   [:providedByFrontend :boolean]])

(def Node
  [:map
   [:name :string]
   [:comment :string]
   [:schema :string]

   [:schemaIndex :int]
   [:isAbstract :boolean]

   [:extends [:vector :string]]
   [:properties [:vector :string]]
   [:allProperties [:vector :string]]
   [:cardinalities [:vector :string]]
   [:inheritedProperties
    [:vector
     [:map
      [:baseType :string]
      [:name :string]]]]
   [:containedNodes
    [:vector
     [:map
      [:name :string]
      [:type :string]
      [:cardinality :string]]]]])

(def Edge
  [:map
   [:name :string]
   [:comment :string]
   [:schema :string]])

(def Property
  [:map
   [:name :string]
   [:comment {:optional true} :string]
   [:schema :string]])

(def CpgSchema
  [:map
   [:schemas
    [:vector [:ref #'Layer]]]
   [:nodes
    [:vector [:ref #'Node]]]
   [:edges
    [:vector [:ref #'Edge]]]
   [:properties
    [:vector [:ref #'Property]]]])

(def ^:private strict-transformer
  (mt/transformer
   mt/strip-extra-keys-transformer))

(def ^:private coerce-cpg-schema
  (m/coercer CpgSchema strict-transformer))

(def text-color "#000000")

(def styles
  [(xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Category" :ValueLabel "Layer"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Layer')"})
                (style/property-setter-elements  {:Background "#d6ac84"
                                                  :Foreground text-color}))
   (xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Category" :ValueLabel "Node"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Node')"})
                (style/property-setter-elements  {:Background "#4ec001"
                                                  :Foreground text-color}))
   (xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Category" :ValueLabel "Edge"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Edge')"})
                (style/property-setter-elements  {:Background "#46a2ca"
                                                  :Foreground text-color}))
   (xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Category" :ValueLabel "Property"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Property')"})
                (style/property-setter-elements  {:Background "#f3dcda"
                                                  :Foreground text-color}))])

(defn layer->id [name]
  (str "layer-" name))

(defn node->id [name]
  (str "node-" name))

(defn edge->id [name]
  (str "edge-" name))

(defn property->id [name]
  (str "property-" name))

(defn ->dgml [data]
  (xml/element ::dgml/DirectedGraph
               {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
               (xml/element ::dgml/Nodes {}
                            (concat
                             (for [layer (:schemas data)]
                               (let [{:keys [description]} layer]
                                 (xml/element ::dgml/Node
                                              {:Id (layer->id (:name layer))
                                               :Label (:name layer)
                                               :Category "Layer"
                                               :Description description
                                               :Group "Expanded"})))
                             (for [node (:nodes data)]
                               (xml/element ::dgml/Node
                                            {:Id (node->id (:name node))
                                             :Label (:name node)
                                             :Category "Node"
                                             :Description (:comment node)}))
                             (for [edge (:edges data)]
                               (xml/element ::dgml/Node
                                            {:Id (edge->id (:name edge))
                                             :Label (:name edge)
                                             :Category "Edge"
                                             :Description (:comment edge)}))
                             (for [property (:properties data)]
                               (xml/element ::dgml/Node
                                            {:Id (property->id (:name property))
                                             :Label (:name property)
                                             :Category "Property"
                                             :Description (:comment property)}))))
               (xml/element ::dgml/Links {}
                            (concat
                             (for [node (:nodes data)]
                               (let [source (layer->id (:schema node))
                                     target (node->id (:name node))]
                                 (xml/element ::dgml/Link {:Source source :Target target :Category "Contains"})))
                             (for [edge (:edges data)]
                               (let [source (layer->id (:schema edge))
                                     target (edge->id (:name edge))]
                                 (xml/element ::dgml/Link {:Source source :Target target :Category "Contains"})))
                             (for [property (:properties data)]
                               (let [source (layer->id (:schema property))
                                     target (property->id (:name property))]
                                 (xml/element ::dgml/Link {:Source source :Target target :Category "Contains"})))
                             (->> (:nodes data)
                                  (mapcat (fn [node]
                                            (let [node-id (node->id (:name node))]
                                              (concat
                                               (map (fn [property cardinality]
                                                      (xml/element ::dgml/Link {:Source node-id
                                                                                :Target (property->id property)
                                                                                :Cardinality cardinality}))
                                                    (:allProperties node) (:cardinalities node))
                                               (for [extended (:extends node)]
                                                 (xml/element ::dgml/Link {:Source node-id
                                                                           :Target (node->id extended)
                                                                           :Label "extends"})))))))))
               (xml/element ::dgml/Styles {} styles)))

(comment
  ;; curl -o tmp/cpg-schema.json https://raw.githubusercontent.com/joernio/cpg-spec-website/refs/heads/main/static/json/schema.json
  (def data (j/read-value (slurp "tmp/cpg-schema.json") j/keyword-keys-object-mapper))

  (let [output-file "../../../../shared/cpg-schema.dgml"]
    (sdgml/write-to-file output-file (->dgml data)))

  (->> (:nodes data)
       (filter (fn [node]
                 (pos? (count (:containedNodes node)))))))

(comment
  (= data (coerce-cpg-schema data))

  (let [data' (assoc data :foo 123)]
    (not= data' (coerce-cpg-schema data')))

  (require 'malli.dev.pretty)
  (malli.dev.pretty/explain CpgSchema data))
