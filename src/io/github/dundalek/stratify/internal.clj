(ns io.github.dundalek.stratify.internal
  (:require
   [clojure.data.xml :as xml]
   [clojure.set :as set]
   [clojure.string :as str]
   [io.github.dundalek.stratify.codecov :as codecov]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.kondo :as kondo]
   [io.github.dundalek.stratify.style :as style]
   [loom.attr :as la]
   [loom.graph :as lg]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml])
  (:import
   (java.util.regex Pattern)))

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

(defn add-clustered-namespace-hierarchy-path-based [g prefix]
  (let [separator "/"
        split-pattern (re-pattern (Pattern/quote separator))
        opts {:split #(str/split (str/replace-first % prefix "") split-pattern)
              :join #(str prefix (str/join separator %))}]
    (reduce (partial add-clustered-namespace-node opts)
            g
            (lg/nodes g))))

(defn- analysis->own-var-usages [analysis]
  (let [{:keys [namespace-definitions var-usages]} analysis
        own-namespace? (->> namespace-definitions
                            (map :name)
                            (into #{}))]
    (->> var-usages
         (remove (fn [{:keys [to]}]
                   (not (own-namespace? to)))))))

(defn- var-edges [var-usages ns->str]
  (->> var-usages
       (map (fn [{:keys [from to name from-var]}]
              [(cond-> (ns->str from)
                 from-var (str "/" from-var))
               (str (ns->str to) "/" name)]))))

(defn- var->category [{:keys [defined-by->lint-as]}]
  (case (some-> defined-by->lint-as name)
    ("defn" "defn-" "defmulti") "Function"
    "defmacro" "Macro"
    ;; All others in the same category for now: def, defonce, defprotocol, defrecord, deftype, deftest
    "Var"))

(def coverage-styles
  [(xml/element ::dgml/Style {:TargetType "Node" :GroupLabel "Coverage" :ValueLabel "Good"}
                (xml/element ::dgml/Condition {:Expression "HasValue('Coverage') and Coverage > 80"})
                (xml/element ::dgml/Setter {:Property "Background" :Value "Green"}))

   (xml/element ::dgml/Style {:TargetType "Node" :GroupLabel "Coverage" :ValueLabel "Ok"}
                (xml/element ::dgml/Condition {:Expression "HasValue('Coverage') and Coverage > 50"})
                (xml/element ::dgml/Setter {:Property "Background"
                                            :Expression "Color.FromRgb(180 * Math.Max(1, (80 - Coverage) / 30), 180, 0)"}))
   (xml/element ::dgml/Style {:TargetType "Node" :GroupLabel "Coverage" :ValueLabel "Bad"}
                (xml/element ::dgml/Condition {:Expression "HasValue('Coverage')"})
                (xml/element ::dgml/Setter {:Property "Background"
                                            :Expression "Color.FromRgb(180, 180 * Coverage / 50, 0)"}))
   (xml/element ::dgml/Style {:TargetType "Node" :GroupLabel "Coverage" :ValueLabel "Unknown"}
                (style/property-setter-elements  {:Background "#686868"
                                                  :Foreground "#FFFFFF"}))])

(defn analysis->graph [{:keys [analysis flat-namespaces include-dependencies insert-namespace-node line-coverage]}]
  (when (empty? (:namespace-definitions analysis))
    (throw (ex-info (str "There are no defined namespaces in analysis.\n"
                         "Did you pass correct source paths?")
                    {:code ::no-source-namespaces})))
  (let [var-usages (->> (if include-dependencies
                          (:var-usages analysis)
                          (analysis->own-var-usages analysis))
                        ;; print warning when unknown namespace?
                        (remove #(= (:to %) :clj-kondo/unknown-namespace)))
        g (lg/digraph (kondo/->graph (update analysis :namespace-definitions
                                             concat (when include-dependencies
                                                      (->> var-usages (map (fn [{:keys [to]}] {:name to})))))))
        g (cond-> g
            (not flat-namespaces) (add-clustered-namespace-hierarchy "."))
        g (if-not line-coverage
            g
            (reduce (fn [g {:keys [name filename]}]
                      (la/add-attr g (str name)
                                   :line-coverage (line-coverage filename)))
                    g
                    (:namespace-definitions analysis)))
        g (reduce
           (fn [g node-id]
             (cond-> (la/add-attr g node-id :category "Namespace")
               flat-namespaces (la/add-attr node-id :label node-id)))
           g
           (lg/nodes g))
        namespace-with-nested-namespace? (->> (lg/nodes g)
                                              (map #(la/attr g % :parent))
                                              set)
        namespaces-with-both (when insert-namespace-node
                               (set/intersection
                                namespace-with-nested-namespace?
                                ;; namespaces with vars
                                (->> (concat
                                      (->> var-usages
                                           (map (comp str :to)))
                                      (->> (:var-definitions analysis)
                                           (map (comp str :ns))))
                                     set)))
        [ns->str g] (if (empty? namespaces-with-both)
                      [str g]
                      (let [ns->str (fn [ns-sym]
                                      (let [ns-str (str ns-sym)]
                                        (if (contains? namespaces-with-both ns-str)
                                          (str ns-str "." insert-namespace-node)
                                          ns-str)))]
                        [ns->str
                         (reduce
                          (fn [g id]
                            (let [node-id (ns->str id)]
                              (-> g
                                  (lg/add-nodes node-id)
                                  (la/add-attr node-id :category "Namespace")
                                  (la/add-attr node-id :label insert-namespace-node)
                                  (la/add-attr node-id :parent (str id))
                                  (la/add-attr node-id :name (str id)))))
                          g
                          namespaces-with-both)]))
        g (reduce
           (fn [g {:keys [name to]}]
             (let [to-str (ns->str to)
                   id (str to-str "/" name)]
               (-> g
                   (lg/add-nodes id)
                   (la/add-attr id :label (str name))
                   (la/add-attr id :parent to-str)
                   (la/add-attr id :category "Var"))))
           g
           var-usages)
        g (reduce
           (fn [g definition]
             (let [{:keys [filename row end-row]} definition
                   ns-str (ns->str (:ns definition))
                   id (str ns-str "/" (:name definition))]
               (cond-> (-> g
                           (lg/add-nodes id)
                           (la/add-attr id :label (str (:name definition)))
                           (la/add-attr id :parent ns-str)
                           (la/add-attr id :category (var->category definition))
                           (la/add-attr id :defined-by (some-> (:defined-by->lint-as definition) str))
                           (la/add-attr id :access (if (:private definition) "Private" "Public"))
                           (la/add-attr id :name (str (:ns definition) "/" (:name definition))))
                 line-coverage
                 ;; 1-indexed, inclusive end
                 (la/add-attr id :line-coverage (line-coverage filename (dec row) end-row)))))
           g
           (:var-definitions analysis))
        g (lg/add-edges* g (var-edges var-usages ns->str))]
    {:g g
     :namespace-with-nested-namespace? namespace-with-nested-namespace?}))

(defn analysis->nodes-links [{:keys [analysis flat-namespaces include-dependencies insert-namespace-node line-coverage]}]
  (let [{:keys [g namespace-with-nested-namespace?]} (analysis->graph {:analysis analysis
                                                                       :flat-namespaces flat-namespaces
                                                                       :include-dependencies include-dependencies
                                                                       :insert-namespace-node insert-namespace-node
                                                                       :line-coverage line-coverage})]

    {:nodes
     (for [node (lg/nodes g)]
       (xml/element ::dgml/Node
                    (cond-> {:Id node
                             :Label (la/attr g node :label)
                             :Category (la/attr g node :category)

                                                      ;; Custom non-DGML attributes
                                                      ;; Need to aware of not conflicting with bultins since VS Editor does not support xml-namespaced attributes
                             :Name (or (la/attr g node :name) node)}

                      (= (la/attr g node :category) "Namespace")
                      (assoc :Group (if (namespace-with-nested-namespace? node) "Expanded" "Collapsed"))

                      (la/attr g node :access)
                      (assoc :Access (la/attr g node :access))

                      (la/attr g node :defined-by)
                      (assoc :DefinedBy (la/attr g node :defined-by))

                      (la/attr g node :line-coverage)
                      (assoc :Coverage (la/attr g node :line-coverage)))))
     :links
     (concat
      (for [[source target] (lg/edges g)]
        (xml/element ::dgml/Link {:Source source :Target target}))
      (->> (lg/nodes g)
           (keep (fn [node-id]
                   (when-some [parent (la/attr g node-id :parent)]
                     (xml/element ::dgml/Link {:Source parent :Target node-id :Category "Contains"}))))))}))

(defn analysis->dgml [{:keys [analysis flat-namespaces include-dependencies insert-namespace-node line-coverage]}]
  (let [{:keys [nodes links]} (analysis->nodes-links {:analysis analysis
                                                      :flat-namespaces flat-namespaces
                                                      :include-dependencies include-dependencies
                                                      :insert-namespace-node insert-namespace-node
                                                      :line-coverage line-coverage})]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {} nodes)
                 (xml/element ::dgml/Links {} links)
                 (xml/element ::dgml/Styles {} (if line-coverage
                                                 coverage-styles
                                                 style/styles)))))

(defn coerce-options [opts]
  (let [{:keys [source-paths flat-namespaces include-dependencies insert-namespace-node coverage-file]} opts]
    {:analysis (kondo/analysis source-paths)
     :flat-namespaces (boolean flat-namespaces)
     :include-dependencies (boolean include-dependencies)
     :insert-namespace-node insert-namespace-node
     :line-coverage (when coverage-file
                      (codecov/make-line-coverage-lookup
                       {:coverage-file coverage-file
                        :strip-prefixes source-paths}))}))

(defn extract-graph [opts]
  (:g (analysis->graph (coerce-options opts))))

(defn extract [{:keys [output-file] :as opts}]
  (let [data (analysis->dgml (coerce-options opts))]
    (sdgml/write-to-file output-file data)))

(comment
  (extract-graph
   {:source-paths ["src"]})

  (extract
   {:source-paths ["src"]
    :output-file "target/out.dgml"})

  (extract
   {:source-paths ["src"]
    :output-file "../../shared/coverage.dgml"
    :coverage-file "target/coverage/codecov.json"})

  (def analysis (kondo/analysis ["test/resources/nested/src"]))
  (def analysis (kondo/analysis ["src"]))

  (->> analysis
       :var-usages)

  (->> analysis
       :var-definitions
       first)

  (-> (kondo/->graph analysis)
      lg/digraph
      (add-clustered-namespace-hierarchy "."))

  (def coverage-file "target/coverage/codecov.json")
  (def lookup (some-> coverage-file codecov/make-line-coverage-raw-lookup))
  (:g (analysis->graph {:analysis analysis
                        :line-coverage (fn [filename & args]
                                         (apply lookup (str/replace-first filename "src/" "") args))})))
