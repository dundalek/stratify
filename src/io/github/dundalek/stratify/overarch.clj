(ns io.github.dundalek.stratify.overarch
  (:require
   [clojure.data.xml :as xml]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.style :refer [property-setter-elements]]
   [org.soulspace.overarch.adapter.repository.file-model-repository :as repository]
   [org.soulspace.overarch.domain.model :as model]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

(defn read-model [source-paths]
  (->> source-paths
       (mapcat repository/read-model)
       (model/build-model)))

(defn ->dgml [model]
  (let [parent? (->> model
                     :relations
                     (filter (comp #{:contained-in} :el))
                     (map :to)
                     (into #{}))]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {}
                              (for [node (:nodes model)]
                                (let [{:keys [id el] node-name :name} node]
                                  (xml/element ::dgml/Node
                                               (cond-> (merge {:Id (str id)
                                                               :Label node-name
                                                               :Category (name el)}
                                                              (update-vals (dissoc node :id :el :ct) str))
                                                 (parent? id) (assoc :Group "Expanded"))))))
                 (xml/element ::dgml/Links {}
                              (concat
                               (for [link (->> model :relations)]
                                 (let [{:keys [from to el] link-name :name} link
                                       attrs (update-vals (dissoc link :id :el :from :to :name) str)]
                                   (if (= :contained-in el)
                                     ;; Source and Target are flipped because we are reversing the direction
                                     ;; from "contained-in" relation to "Contains" link category
                                     ;; so that DGML will treat it as expandable/collapsible hierarchy.
                                     (xml/element ::dgml/Link (merge {:Source (str to)
                                                                      :Target (str from)
                                                                      :Category "Contains"
                                                                      :Label "contains"}
                                                                     attrs))
                                     (xml/element ::dgml/Link (merge {:Source (str from)
                                                                      :Target (str to)
                                                                      :Category (name el)
                                                                      :Label link-name}
                                                                     attrs)))))))
                 (xml/element ::dgml/Styles {}
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Person" :ValueLabel "External"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('person') and external = 'true'"})
                                           (property-setter-elements  {:Background "#686868"
                                                                       :Foreground "#FFFFFF"}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Person" :ValueLabel ""}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('person')"})
                                           (property-setter-elements  {:Background "#214377"
                                                                       :Foreground "#FFFFFF"}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "System" :ValueLabel "External"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('system') and external = 'true'"})
                                           (property-setter-elements  {:Background "#989898"
                                                                       :Foreground "#FFFFFF"}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "System" :ValueLabel ""}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('system')"})
                                           (property-setter-elements  {:Background "#3166B7"
                                                                       :Foreground "#FFFFFF"}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "System" :ValueLabel ""}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('container')"})
                                           (property-setter-elements  {:Background "#568AD0"
                                                                       :Foreground "#FFFFFF"}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "System" :ValueLabel ""}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('component')"})
                                           (property-setter-elements  {:Background "#8EB8EC"}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "System" :ValueLabel ""}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('use-case')"})
                                           (property-setter-elements  {:Background "#B9BAFB"}))))))

(defn extract [{:keys [source-paths output-file]}]
  (let [model (try
                (read-model source-paths)
                (catch Throwable t
                  (throw (ex-info "Failed to load Overarch model." {:code ::invalid-input} t))))
        data (->dgml model)]
    (sdgml/write-to-file output-file data)))

(comment

  (def source-paths ["target/projects/overarch/models/banking"])
  (def source-paths ["target/projects/overarch/models/big-bank"])

  (def model (read-model source-paths))

  (->> model
       :relations
       (map :el)
       frequencies)

  (tap> model)

  (->> model
       :relations
       (filter (comp #{:contained-in} :el)))

  (->> model
       :nodes
       (map #(dissoc % :ct)))

  (extract {:source-paths ["target/projects/overarch/models/banking"]
            :output-file "banking.dgml" #_"../../shared/overarch-banking.dgml"}))
