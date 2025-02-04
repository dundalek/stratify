(ns io.github.dundalek.stratify.polylith
  (:require
   [clojure.data.xml :as xml]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.internal :as internal :refer [property-setter-elements]]
   [io.github.dundalek.stratify.kondo :as kondo]
   [polylith.clj.core.workspace.interface :as workspace]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

(def flags [:src])

(defn pick-flaged [flags items-map]
  (reduce
   (fn [s flag]
     (into s (get items-map flag)))
   #{}
   flags))

(defn project-id [name]
  (str "project/" name))

(defn base-id [name]
  (str "base/" name))

(defn component-id [name]
  (str "component/" name))

(defn ->dgml [workspace]
  (let [{:keys [ws-dir]} workspace
        component-analyses (->> workspace
                                :components
                                (map (fn [{:keys [name paths]}]
                                       (let [prefix (str ws-dir "/components/" name "/")
                                             source-paths (->> (pick-flaged flags paths)
                                                               (map #(str prefix %)))]
                                         [name (:analysis (kondo/run-kondo source-paths))])))
                                (into {}))
        {:keys [nodes links]} (internal/analysis->nodes-links {:analysis (apply merge-with concat (vals component-analyses))})]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {}
                              (concat
                               (for [{:keys [name]} (:projects workspace)]
                                 (xml/element ::dgml/Node
                                              {:Id (project-id name)
                                               :Label name
                                               :Category "Project"}))
                               (for [{:keys [name]} (:bases workspace)]
                                 (xml/element ::dgml/Node
                                              {:Id (base-id name)
                                               :Label name
                                               :Category "Base"}))
                               (for [{:keys [name]} (:components workspace)]
                                 (xml/element ::dgml/Node
                                              {:Id (component-id name)
                                               :Label name
                                               :Category "Component"
                                               :Group "Expanded"}))
                               nodes))
                 (xml/element ::dgml/Links {}
                              (concat
                               (->> (:projects workspace)
                                    (mapcat (fn [{:keys [name component-names]}]
                                              (let [id (project-id name)]
                                                (for [dep (pick-flaged flags component-names)]
                                                  (xml/element ::dgml/Link {:Source id
                                                                            :Target (component-id dep)}))))))
                               (->> (:projects workspace)
                                    (mapcat (fn [{:keys [name base-names]}]
                                              (let [id (project-id name)]
                                                (for [dep (pick-flaged flags base-names)]
                                                  (xml/element ::dgml/Link {:Source id
                                                                            :Target (base-id dep)}))))))
                               (->> (:bases workspace)
                                    (mapcat (fn [{:keys [name interface-deps]}]
                                              (let [id (base-id name)]
                                                (for [dep (pick-flaged flags interface-deps)]
                                                  (xml/element ::dgml/Link {:Source id
                                                                            :Target (component-id dep)}))))))
                               (->> (:components workspace)
                                    (mapcat (fn [{:keys [name interface-deps]}]
                                              (let [id (component-id name)]
                                                (for [dep (pick-flaged flags interface-deps)]
                                                  (xml/element ::dgml/Link {:Source id
                                                                            :Target (component-id dep)}))))))
                               (->> (:components workspace)
                                    (mapcat (fn [{:keys [name]}]
                                              (let [namespaces (get-in component-analyses [name :namespace-definitions])
                                                    source-id (component-id name)]
                                                (for [{namespace-name :name} namespaces]
                                                  (xml/element ::dgml/Link {:Source source-id
                                                                            :Target (str namespace-name)
                                                                            :Category "Contains"}))))))
                               links))
                 (xml/element ::dgml/Styles {}
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Project"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('Project')"})
                                           (property-setter-elements {:Background "#3166B7"
                                                                      :Foreground "#FFFFFF"}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Base"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('Base')"})
                                           (property-setter-elements {:Background "#568AD0"
                                                                      :Foreground "#FFFFFF"}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Component"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('Component')"})
                                           (property-setter-elements {:Background "#8EB8EC"}))
                              internal/styles))))

(defn extract [{:keys [source-paths output-file]}]
  (let [workspace (workspace/workspace {:ws-dir (first source-paths)})
        data (->dgml workspace)]
    (sdgml/write-to-file output-file data)))

(comment
  (extract {:source-paths ["target/projects/clojure-polylith-realworld-example-app"]
            :output-file "../../shared/polylith-realworld.dgml"})

  (def w (workspace/workspace {:ws-dir  "target/projects/clojure-polylith-realworld-example-app"}))
  (->dgml w)

  (let [component-analyses (->> w
                                :components
                                (map (fn [{:keys [name paths]}]
                                       [name (:analysis (kondo/run-kondo (pick-flaged flags paths)))]))
                                (into {}))]
    (tap> (apply merge-with concat (vals component-analyses))))

  (def p (-> w :projects first))

  (def result (kondo/run-kondo (-> w :components first :paths :src)))
  (-> result :analysis keys)

  (pick-flaged
   [:src]
   (-> p :component-names))

  (->> w
       :projects
       (map (fn [{:keys [name base-names component-names]}]
              {:name name
               :components (pick-flaged flags component-names)
               :bases (pick-flaged flags base-names)})))

  (->> w
       :components
       (map (fn [{:keys [name interface-deps]}]
              {:name name
               :interface-deps (pick-flaged flags interface-deps)}))))
