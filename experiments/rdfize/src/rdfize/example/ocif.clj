(ns rdfize.example.ocif
  (:require
   [clojure.string :as str]
   [org.canvasprotocol.$.ocif.v0%2E5 :as-alias ocif]
   [org.w3.www.$.1999.02.22-rdf-syntax-ns.$$ :as-alias rdf]))

(defn- id->kwi [base id]
  (keyword base id))

(defn- node->triples [node base]
  (let [id-kwi (id->kwi base (:id node))]
    (concat
     [[id-kwi ::rdf/type ::ocif/Node]]
     (when (:position node)
       [[id-kwi ::ocif/position (str (first (:position node)) "," (second (:position node)))]])
     (when (:size node)
       [[id-kwi ::ocif/size (str (first (:size node)) "," (second (:size node)))]])
     (when (:resource node)
       [[id-kwi ::ocif/resource (id->kwi base (:resource node))]])
     (when (:rotation node)
       [[id-kwi ::ocif/rotation (:rotation node)]])
     (when (:relation node)
       [[id-kwi ::ocif/relation (id->kwi base (:relation node))]])
     (mapcat (fn [data-item]
               [[id-kwi ::ocif/hasExtension (str (:type data-item))]
                [id-kwi ::ocif/extensionData (pr-str data-item)]])
             (:data node [])))))

(defn- relation->triples [relation base]
  (let [id-kwi (id->kwi base (:id relation))]
    (concat
     [[id-kwi ::rdf/type ::ocif/Relation]]
     (when (:node relation)
       [[id-kwi ::ocif/visualNode (id->kwi base (:node relation))]])
     (mapcat (fn [data-item]
               (let [type (:type data-item)]
                 (concat
                  [[id-kwi ::ocif/relationType type]]
                  (when (:start data-item)
                    [[id-kwi ::ocif/start (id->kwi base (:start data-item))]])
                  (when (:end data-item)
                    [[id-kwi ::ocif/end (id->kwi base (:end data-item))]])
                  (when (:directed data-item)
                    [[id-kwi ::ocif/directed (:directed data-item)]])
                  (when (:rel data-item)
                    [[id-kwi ::ocif/semanticRelation (:rel data-item)]])
                  (when (:members data-item)
                    (map (fn [member]
                           [id-kwi ::ocif/member (id->kwi base member)])
                         (:members data-item)))
                  (when (:cascadeDelete data-item)
                    [[id-kwi ::ocif/cascadeDelete (:cascadeDelete data-item)]])
                  (when (:node data-item)
                    [[id-kwi ::ocif/visualNodeInData (id->kwi base (:node data-item))]]))))
             (:data relation [])))))

(defn- resource->triples [resource base]
  (let [id-kwi (id->kwi base (:id resource))]
    (concat
     [[id-kwi ::rdf/type ::ocif/Resource]]
     (mapcat (fn [repr]
               (let [repr-kwi (keyword (str (:id resource) "-repr-" (hash repr)))]
                 (concat
                  [[id-kwi ::ocif/hasRepresentation repr-kwi]
                   [repr-kwi ::rdf/type ::ocif/Representation]]
                  (when (:mimeType repr)
                    [[repr-kwi ::ocif/mimeType (:mimeType repr)]])
                  (when (:content repr)
                    [[repr-kwi ::ocif/content (:content repr)]])
                  (when (:location repr)
                    [[repr-kwi ::ocif/location (:location repr)]]))))
             (:representations resource [])))))

(defn data->triples [data base]
  (vec
   (concat
    [[::ocif/document ::rdf/type ::ocif/Document]
     [::ocif/document ::ocif/version (:ocif data)]]
    (mapcat #(node->triples % base) (:nodes data))
    (mapcat #(relation->triples % base) (:relations data))
    (mapcat #(resource->triples % base) (:resources data)))))

(defn rdf->data [graph base]
  (let [kwi->id (fn [kwi]
                  (when (and (keyword? kwi) (= base (namespace kwi)))
                    (name kwi)))

        get-property (fn [subject prop]
                       (first (get-in graph [subject prop])))

        get-properties (fn [subject prop]
                         (get-in graph [subject prop] #{}))

        extract-position (fn [pos-str]
                           (when pos-str
                             (mapv #(int (Double/parseDouble %)) (str/split pos-str #","))))

        ;; Extract nodes
        nodes (->> graph
                   (filter (fn [[subj props]]
                             (contains? (get props ::rdf/type #{}) ::ocif/Node)))
                   (map (fn [[subj props]]
                          (let [id (kwi->id subj)]
                            (when id
                              (cond-> {:id id}
                                (get-property subj ::ocif/position)
                                (assoc :position (extract-position (get-property subj ::ocif/position)))

                                (get-property subj ::ocif/size)
                                (assoc :size (extract-position (get-property subj ::ocif/size)))

                                (get-property subj ::ocif/resource)
                                (assoc :resource (kwi->id (get-property subj ::ocif/resource)))

                                (get-property subj ::ocif/rotation)
                                (assoc :rotation (get-property subj ::ocif/rotation))

                                (get-property subj ::ocif/relation)
                                (assoc :relation (kwi->id (get-property subj ::ocif/relation)))

                                (seq (get-properties subj ::ocif/extensionData))
                                (assoc :data (->> (get-properties subj ::ocif/extensionData)
                                                  (map #(read-string %))
                                                  vec)))))))
                   (filter identity)
                   vec)

        ;; Extract relations
        relations (->> graph
                       (filter (fn [[subj props]]
                                 (contains? (get props ::rdf/type #{}) ::ocif/Relation)))
                       (map (fn [[subj props]]
                              (let [id (kwi->id subj)]
                                (when id
                                  (cond-> {:id id}
                                    (get-property subj ::ocif/visualNode)
                                    (assoc :node (kwi->id (get-property subj ::ocif/visualNode)))

                                    (get-property subj ::ocif/relationType)
                                    (assoc :data
                                           [(cond-> {:type (get-property subj ::ocif/relationType)}
                                              (get-property subj ::ocif/start)
                                              (assoc :start (kwi->id (get-property subj ::ocif/start)))

                                              (get-property subj ::ocif/end)
                                              (assoc :end (kwi->id (get-property subj ::ocif/end)))

                                              (get-property subj ::ocif/directed)
                                              (assoc :directed (get-property subj ::ocif/directed))

                                              (get-property subj ::ocif/semanticRelation)
                                              (assoc :rel (get-property subj ::ocif/semanticRelation))

                                              (seq (get-properties subj ::ocif/member))
                                              (assoc :members (->> (get-properties subj ::ocif/member)
                                                                   (map kwi->id)
                                                                   (filter identity)
                                                                   vec))

                                              (get-property subj ::ocif/cascadeDelete)
                                              (assoc :cascadeDelete (get-property subj ::ocif/cascadeDelete))

                                              (get-property subj ::ocif/visualNodeInData)
                                              (assoc :node (kwi->id (get-property subj ::ocif/visualNodeInData))))]))))))
                       (filter identity)
                       vec)

        ;; Extract resources
        resources (->> graph
                       (filter (fn [[subj props]]
                                 (contains? (get props ::rdf/type #{}) ::ocif/Resource)))
                       (map (fn [[subj props]]
                              (let [id (kwi->id subj)
                                    repr-subjects (get-properties subj ::ocif/hasRepresentation)]
                                (when id
                                  {:id id
                                   :representations
                                   (->> repr-subjects
                                        (map (fn [repr-subj]
                                               (cond-> {}
                                                 (get-property repr-subj ::ocif/mimeType)
                                                 (assoc :mimeType (get-property repr-subj ::ocif/mimeType))

                                                 (get-property repr-subj ::ocif/content)
                                                 (assoc :content (get-property repr-subj ::ocif/content))

                                                 (get-property repr-subj ::ocif/location)
                                                 (assoc :location (get-property repr-subj ::ocif/location)))))
                                        (filter #(or (:mimeType %) (:content %) (:location %)))
                                        vec)}))))
                       (filter identity)
                       vec)]

    (cond-> {}
      (get-property ::ocif/document ::ocif/version)
      (assoc :ocif (get-property ::ocif/document ::ocif/version))

      (seq nodes)
      (assoc :nodes nodes)

      (seq relations)
      (assoc :relations relations)

      (seq resources)
      (assoc :resources resources))))
