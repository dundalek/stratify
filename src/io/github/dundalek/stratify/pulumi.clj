(ns io.github.dundalek.stratify.pulumi
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.style :as style :refer [property-setter-elements theme]]
   [babashka.json :as json]
   [loom.attr :as la]
   [loom.graph :as lg]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

(def Urn :string)
(def Resource [:map
               [:urn #'Urn]
               [:parent {:optional true} #'Urn]
               [:custom {:optional true} :boolean]
               [:dependencies {:optional true}
                [:sequential #'Urn]]
               [:propertyDependencies {:optional true}
                [:map-of :keyword [:sequential #'Urn]]]])
(def Step
  [:map
   [:op [:enum "create" "delete" "same" "update"]]
   [:newState {:optional true} #'Resource]])
(def State
  [:orn
   ;; `pulumi stack export`
   [:deployment [:map
                 [:deployment
                  [:map
                   [:resources [:sequential #'Resource]]]]]]
   ;; SST report outputs state as checkpoint
   [:checkpoint [:map
                 [:deployment
                  [:map
                   [:latest
                    [:map
                     [:resources [:sequential #'Resource]]]]]]]]
   ;; steps are from `pulumi preview --json`
   [:steps [:map
            [:steps [:sequential #'Step]]]]])

(def ^:private strict-json-transformer
  (mt/transformer
   mt/strip-extra-keys-transformer
   mt/json-transformer))

(def ^:private parse-and-coerce-state
  (comp
   (m/parser State)
   (m/coercer State strict-json-transformer)))

(defn- parse-resources [data]
  (let [[tag value] (parse-and-coerce-state data)]
    (case tag
      :deployment (-> value :deployment :resources)
      :checkpoint (-> value :checkpoint :latest :resources)
      :steps (->> value :steps (keep :newState)))))

(defn- parse-resources-file [input-file]
  (let [input (try
                (json/read-str (slurp input-file) {:key-fn keyword})
                (catch Throwable t
                  (throw (ex-info "Failed to parse Pulumi file." {:code ::failed-to-parse} t))))
        resources (try
                    (parse-resources input)
                    (catch Throwable t
                      (if (= (:type (ex-data t)) :malli.core/coercion)
                        (throw (ex-info (str "Failed to load Pulumi resources.\n\n"
                                             (-> t ex-data :data :explain me/humanize))
                                        {:code ::invalid-input} t))
                        (throw t))))]
    resources))

(def ^:private styles
  [(xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Custom" :ValueLabel "False"}
                (xml/element ::dgml/Condition {:Expression "Custom = 'False'"})
                (property-setter-elements  {:Background (::style/namespace-color theme)
                                            :Stroke (::style/namespace-stroke-color theme)
                                            :Foreground (::style/node-text-color theme)}))
   (xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Custom" :ValueLabel "True"}
                (xml/element ::dgml/Condition {:Expression "Custom = 'True'"})
                (property-setter-elements {:Background (::style/function-color theme)
                                           :Stroke (::style/function-stroke-color theme)}))])

(defn- add-edges-with-attrs [g edges]
  (let [g (lg/add-edges* g (map first edges))]
    (reduce (fn [g [edge attrs]]
              (reduce (fn [g [k v]]
                        (la/add-attr g edge k v))
                      g attrs))
            g edges)))

(defn- urn->resource-name [s]
  (last (str/split s #"::")))

(defn- ->dgml [resources]
  (let [node-with-children? (->> resources (keep :parent) set)
        node-attrs (->> resources
                        (map (fn [resource]
                               (let [{:keys [urn custom]} resource
                                     label (urn->resource-name urn)]
                                 [urn (cond-> {:Label label
                                               :Urn urn
                                               :Custom (if custom "True" "False")}
                                        (node-with-children? urn)
                                        (assoc :Group "Expanded"))])))
                        (into {}))
        dependency-edges (->> resources
                              (mapcat (fn [{:keys [urn dependencies propertyDependencies]}]
                                        (concat
                                         (for [dep dependencies]
                                           [[urn dep] {}])
                                         (->> propertyDependencies
                                              (mapcat (fn [[k deps]]
                                                        (for [dep deps]
                                                          [[urn dep] {:Label (name k)}]))))))))
        parent-hierarchy-edges (->> resources
                                    (keep (fn [{:keys [urn parent]}]
                                            (when parent
                                              [[parent urn] {:Category "Contains"}]))))
        g (-> (lg/digraph)
              (lg/add-nodes* (keys node-attrs))
              (assoc :attrs node-attrs)
              (add-edges-with-attrs dependency-edges)
              (add-edges-with-attrs parent-hierarchy-edges))]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {}
                              (for [node (lg/nodes g)]
                                (xml/element ::dgml/Node (merge {:Id node}
                                                                (la/attrs g node)))))
                 (xml/element ::dgml/Links {}
                              (for [edge (lg/edges g)]
                                (let [[source target] edge]
                                  (xml/element ::dgml/Link (merge {:Source source :Target target}
                                                                  (la/attrs g edge))))))
                 (xml/element ::dgml/Styles {} styles))))

(defn extract [{:keys [input-file output-file]}]
  (let [resources (parse-resources-file input-file)
        data (->dgml resources)]
    (sdgml/write-to-file output-file data)))

(comment
  (extract {:input-file "test/resources/pulumi/sample-preview-creates.json"
            :output-file "test/resources/pulumi/sample-preview-creates.dgml"})

  (extract {:input-file "test/resources/pulumi/sample-export.json"
            :output-file "test/resources/pulumi/sample-export.dgml"})

  (extract {:input-file "../pulumi-examples/aws-ts-pern-voting-app/preview.json"
            :output-file "../../shared/pulumi-aws-ts-pern-voting-app.dgml"})

  (extract {:input-file "../sst/examples/aws-astro/.sst/report/state.json"
            :output-file "../../shared/sst-aws-astro.dgml"})

  (def input (json/read-str (slurp "test/resources/pulumi/sample-export.json") {:key-fn keyword}))
  (def resources (parse-resources input))

  (->> resources
       (mapcat keys)
       frequencies))
