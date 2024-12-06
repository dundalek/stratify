(ns io.github.dundalek.stratify.pulumi
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [dorothy.core :as-alias dc]
   [io.github.dundalek.stratify.internal :refer [property-setter-elements]]
   [io.github.dundalek.stratify.style :as style :refer [theme]]
   [jsonista.core :as j]
   [loom.attr :as la]
   [loom.graph :as lg]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

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

(defn- parse-resources [data]
  (cond
    (:deployment data) (-> data :deployment :resources) ; `pulumi stack export`
    (:checkpoint data) (-> data :checkpoint :latest :resources) ; SST report outputs state as checkpoint
    ;; steps are from `pulumi preview --json`
    (:steps data) (->> data :steps
                       (keep (fn [{:keys [op newState]}]
                               (assert (#{"create" "delete" "same" "update"} op)
                                       (str "Unknown op: " op))
                               newState)))))

(defn- ->dgml [input]
  (let [resources (parse-resources input)
        node-with-children? (->> resources (keep :parent) set)
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
  (let [input (j/read-value (slurp input-file) j/keyword-keys-object-mapper)
        data (->dgml input)]
    (if (instance? java.io.Writer output-file)
      (xml/indent data output-file)
      (with-open [out (io/writer output-file)]
        (xml/indent data out)))))

(comment
  (extract {:input-file "test/resources/pulumi/sample-preview-creates.json"
            :output-file "test/resources/pulumi/sample-preview-creates.dgml"})

  (extract {:input-file "test/resources/pulumi/sample-export.json"
            :output-file "test/resources/pulumi/sample-export.dgml"})

  (extract {:input-file "../pulumi-examples/aws-ts-pern-voting-app/preview.json"
            :output-file "../../shared/pulumi-aws-ts-pern-voting-app.dgml"})

  (extract {:input-file "../sst/examples/aws-astro/.sst/report/state.json"
            :output-file "../../shared/sst-aws-astro.dgml"})

  (def input (j/read-value (slurp "test/resources/pulumi/sample-export.json") j/keyword-keys-object-mapper))
  (def resources (parse-resources input))

  (->> resources
       (mapcat keys)
       frequencies))
