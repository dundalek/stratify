(ns io.github.dundalek.stratify.internal
  (:require
   [clj-kondo.core :as clj-kondo]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [io.github.dundalek.stratify.style :as style :refer [theme]]
   [loom.attr :as la]
   [loom.graph :as lg]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml])
  (:import
   (java.util.regex Pattern)))

(defn run-kondo [paths]
  (clj-kondo/run!
   {:lint paths
    :config {:output {:analysis {:keywords true}}}}))

(defn color-add-alpha [color alpha]
  (assert (and (string? color)
               (= (first color) \#)
               (= (count color) 7)))
  (assert (and (string? alpha)
               (= (count alpha) 2)))
  (str "#" alpha (subs color 1)))

(defn- add-clustered-namespace-node [{:keys [split join]} g node-id]
  (loop [g g
         node-id node-id
         segments (split node-id)]
    (if (empty? segments)
      g
      (let [cluster-label (last segments)
            cluster-id (join segments)
            attrs {:label cluster-label}]
        (recur (-> g
                   (lg/add-nodes cluster-id)
                   (assoc-in [:attrs node-id :parent] cluster-id)
                   (assoc-in [:attrs cluster-id] attrs))
               cluster-id
               (butlast segments))))))

(defn add-clustered-namespace-hierarchy [g separator]
  (let [split-pattern (re-pattern (Pattern/quote separator))
        opts {:split #(str/split % split-pattern)
              :join #(str/join separator %)}]
    (reduce (partial add-clustered-namespace-node opts)
            g
            (lg/nodes g))))

(defn property-setter-elements [properties]
  (for [[k v] properties]
    (xml/element ::dgml/Setter {:Property (name k) :Value v})))

(defn analysis->own-var-usages [analysis]
  (let [{:keys [namespace-definitions var-usages]} analysis
        own-namespace? (->> namespace-definitions
                            (map :name)
                            (into #{}))]
    (->> var-usages
         (remove (fn [{:keys [to]}]
                   (not (own-namespace? to)))))))

(defn var-edges [var-usages]
  (->> var-usages
       (map (fn [{:keys [from to name from-var]}]
              [(cond-> (str from)
                 from-var (str "/" from-var))
               (str to "/" name)]))))

(defn ->graph [analysis]
  (let [{:keys [namespace-usages namespace-definitions]} analysis
        own-namespace? (->> namespace-definitions
                            (map :name)
                            (into #{}))
        ;; Have an empty set for each defined namespace as a base,
        ;; so that unconnected namespaces are also included.
        adj (->> namespace-definitions
                 (reduce (fn [m {:keys [name]}]
                           (assoc m (str name) #{}))
                         {}))
        adj (->> namespace-usages
                 (filter (comp own-namespace? :to))
                 (reduce (fn [m {:keys [from to]}]
                           (update m (str from) (fnil conj #{}) (str to)))
                         adj))]
    adj))

(defn var->category [{:keys [defined-by->lint-as]}]
  (case (some-> defined-by->lint-as name)
    ("defn" "defn-" "defmulti") "Function"
    "defmacro" "Macro"
    ;; All others in the same category for now: def, defonce, defprotocol, defrecord, deftype, deftest
    "Var"))

(defn analysis->dgml [{:keys [analysis flat-namespaces include-dependencies]}]
  (let [var-usages (->> (if include-dependencies
                          (:var-usages analysis)
                          (analysis->own-var-usages analysis))
                        ;; print warning when unknown namespace?
                        (remove #(= (:to %) :clj-kondo/unknown-namespace)))
        g (lg/digraph (->graph (update analysis :namespace-definitions
                                       concat (when include-dependencies
                                                (->> var-usages (map (fn [{:keys [to]}] {:name to})))))))
        g (cond-> g
            (not flat-namespaces) (add-clustered-namespace-hierarchy "."))
        g (reduce
           (fn [g node-id]
             (cond-> (la/add-attr g node-id :category "Namespace")
               flat-namespaces (la/add-attr node-id :label node-id)))
           g
           (lg/nodes g))
        namespace-with-children? (->> (lg/nodes g)
                                      (map #(la/attr g % :parent))
                                      set)
        g (reduce
           (fn [g {:keys [name to]}]
             (let [id (str to "/" name)]
               (-> g
                   (lg/add-nodes id)
                   (la/add-attr id :label (str name))
                   (la/add-attr id :parent (str to))
                   (la/add-attr id :category "Var"))))
           g
           var-usages)
        g (reduce
           (fn [g definition]
             (let [id (str (:ns definition) "/" (:name definition))]
               (-> g
                   (lg/add-nodes id)
                   (la/add-attr id :label (str (:name definition)))
                   (la/add-attr id :parent (str (:ns definition)))
                   (la/add-attr id :category (var->category definition))
                   (la/add-attr id :defined-by (some-> (:defined-by->lint-as definition) str))
                   (la/add-attr id :access (if (:private definition) "Private" "Public")))))
           g
           (:var-definitions analysis))
        g (lg/add-edges* g (var-edges var-usages))]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {}
                              (for [node (lg/nodes g)]
                                (xml/element ::dgml/Node
                                             (cond-> {:Id node
                                                      :Label (la/attr g node :label)
                                                      :Category (la/attr g node :category)

                                                      ;; Custom non-DGML attributes
                                                      ;; Need to aware of not conflicting with bultins since VS Editor does not support xml-namespaced attributes
                                                      :Name node}

                                               (= (la/attr g node :category) "Namespace")
                                               (assoc :Group (if (namespace-with-children? node) "Expanded" "Collapsed"))

                                               (la/attr g node :access)
                                               (assoc :Access (la/attr g node :access))

                                               (la/attr g node :defined-by)
                                               (assoc :DefinedBy (la/attr g node :defined-by))))))
                 (xml/element ::dgml/Links {}
                              (concat
                               (for [[source target] (lg/edges g)]
                                 (xml/element ::dgml/Link {:Source source :Target target}))
                               (->> (lg/nodes g)
                                    (keep (fn [node-id]
                                            (when-some [parent (la/attr g node-id :parent)]
                                              (xml/element ::dgml/Link {:Source parent :Target node-id :Category "Contains"})))))))
                 (xml/element ::dgml/Styles {}
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Namespace" :ValueLabel "True"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('Namespace')"})
                                           (property-setter-elements  {:Background (::style/namespace-color theme)
                                                                       :Stroke (::style/namespace-stroke-color theme)
                                                                       :Foreground (::style/node-text-color theme)}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Function" :ValueLabel "Public"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('Function') and Access = 'Public'"})
                                           (property-setter-elements {:Background (::style/function-color theme)
                                                                      :Stroke (::style/function-stroke-color theme)
                                                                      :Foreground (::style/node-text-color theme)}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Function" :ValueLabel "Private"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('Function') and  Access = 'Private'"})
                                           (property-setter-elements {:Background (color-add-alpha (::style/function-color theme) "66")
                                                                      :Stroke (::style/function-stroke-color theme)
                                                                      :StrokeDashArray "3,6"
                                                                      :Foreground (::style/node-text-color theme)}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Macro" :ValueLabel "Public"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('Macro') and Access = 'Public'"})
                                           (property-setter-elements {:Background (::style/macro-color theme)
                                                                      :Stroke (::style/macro-stroke-color theme)
                                                                      :Foreground (::style/node-text-color theme)}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Macro" :ValueLabel "Private"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('Macro') and Access = 'Private'"})
                                           (property-setter-elements {:Background (color-add-alpha (::style/macro-color theme) "66")
                                                                      :Stroke (::style/macro-stroke-color theme)
                                                                      :StrokeDashArray "3,6"
                                                                      :Foreground (::style/node-text-color theme)}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Var" :ValueLabel "Public"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('Var') and Access = 'Public'"})
                                           (property-setter-elements {:Background (::style/var-color theme)
                                                                      :Stroke (::style/var-stroke-color theme)
                                                                      :Foreground (::style/node-text-color theme)}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Node" :GroupLabel "Var" :ValueLabel "Private"}
                                           (xml/element ::dgml/Condition {:Expression "HasCategory('Var') and Access = 'Private'"})
                                           (property-setter-elements {:Background (color-add-alpha (::style/var-color theme) "66")
                                                                      :Stroke (::style/var-stroke-color theme)
                                                                      :StrokeDashArray "3,6"
                                                                      :Foreground (::style/node-text-color theme)}))
                              (xml/element ::dgml/Style
                                           {:TargetType "Link" :GroupLabel "Link" :ValueLabel "Private Reference"}
                                           (xml/element ::dgml/Condition {:Expression "Target.Access = 'Private'"})
                                           (property-setter-elements {:StrokeDashArray "4,2"}))))))

(defn extract [{:keys [source-paths output-file flat-namespaces include-dependencies]}]
  (let [{:keys [analysis]} (run-kondo source-paths)
        data (analysis->dgml {:analysis analysis
                              :flat-namespaces (boolean flat-namespaces)
                              :include-dependencies (boolean include-dependencies)})]
    (if (instance? java.io.Writer output-file)
      (xml/indent data output-file)
      (with-open [out (io/writer output-file)]
        (xml/indent data out)))))

(comment
  (extract
   {:source-paths ["src"]
    :output-file "target/out.dgml"})

  (def result (run-kondo ["test/resources/sample/src"]))

  (->> result
       :analysis
       :var-usages)

  (->> result
       :analysis
       :var-definitions))
