(ns io.github.dundalek.stratify.overarch
  (:require
   [clojure.data.xml :as xml]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [org.soulspace.overarch.domain.model :as model]
   [org.soulspace.overarch.domain.spec :as spec]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]
   [io.github.dundalek.stratify.internal :refer [property-setter-elements]]))

(defn build-model [coll]
  (-> coll
      (spec/check-input-model)
      (model/build-model)))

(defn ->dgml [model]
  (let [parent? (->> model
                     :relations
                     (filter (comp #{:contains} :el))
                     (map :from)
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
                                 (let [{:keys [from to el] link-name :name} link]
                                   (xml/element ::dgml/Link (merge {:Source (str from)
                                                                    :Target (str to)
                                                                    :Category (if (= :contains el)
                                                                                "Contains"
                                                                                (name el))
                                                                    :Label link-name}
                                                                   (update-vals (dissoc link :id :el :from :to) str)))))))
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

(comment

  (def input-data (edn/read-string (slurp "target/projects/overarch/models/banking/model.edn")))
  (def input-data (edn/read-string (slurp "target/projects/overarch/models/big-bank/model.edn")))

  (def model (build-model input-data))

  (->> model
       (map :el)
       frequencies)

  (tap> input-data)
  (tap> model)

  (= input-data (:input-elements model))

  (->> model
       :relations
       (filter (comp #{:contains} :el)))

  (->> model
       :nodes
       (map #(dissoc % :ct)))

  (let [input-data (edn/read-string (slurp "target/projects/overarch/models/banking/model.edn"))
        model (build-model input-data)
        output-file "../../shared/overarch-banking.dgml"
        data (->dgml model)]
    (with-open [out (io/writer output-file)]
      (xml/indent data out))))
