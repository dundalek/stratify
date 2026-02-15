(ns io.github.dundalek.stratify.joern
  "Extract code dependency graphs using Joern CPG (Code Property Graph) library.
   Supports both:
   - Parsing pre-exported GraphSON files
   - Running Joern directly via JAR (JVM only)"
  (:require
   [babashka.json :as json]
   [clojure.data.xml :as xml]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.style :as style]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

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

(defn- extract-vertex-property [vertex-property]
  (-> vertex-property
      (get "@value")
      (get "@value")
      first
      extract-value))

(defn parse-graphson [data]
  (let [{:strs [edges vertices]} (get data "@value")
        vertices-by-id (->> vertices
                            (map (fn [vertex]
                                   (let [id (get-in vertex ["id" "@value"])
                                         label (get vertex "label")
                                         props (->> (get vertex "properties")
                                                    (map (fn [[k v]]
                                                           [(keyword k) (extract-vertex-property v)]))
                                                    (into {}))]
                                     [id (assoc props :id id :label label)])))
                            (into {}))
        edges-list (->> edges
                        (map (fn [edge]
                               {:id (get-in edge ["id" "@value"])
                                :label (get edge "label")
                                :source (get-in edge ["outV" "@value"])
                                :target (get-in edge ["inV" "@value"])
                                :source-label (get edge "outVLabel")
                                :target-label (get edge "inVLabel")})))]
    {:vertices vertices-by-id
     :edges edges-list}))

(defn- synthetic-file-method?
  "Check if method is a synthetic file-level method created by Joern.
   These have names ending with the source file extension."
  [m]
  (let [name (str (:NAME m))]
    (or (str/ends-with? name ".go")
        (str/ends-with? name ".c")
        (str/ends-with? name ".cpp")
        (str/ends-with? name ".h")
        (str/ends-with? name ".hpp")
        (str/ends-with? name ".java")
        (str/ends-with? name ".js")
        (str/ends-with? name ".jsx")
        (str/ends-with? name ".ts")
        (str/ends-with? name ".tsx")
        (str/ends-with? name ".mjs")
        (str/ends-with? name ".cjs")
        (str/ends-with? name ".py"))))

(defn- synthetic-java-method?
  "Check if method is a synthetic Java method (constructor or static initializer)."
  [m]
  (let [name (str (:NAME m))]
    (or (= name "<init>")
        (= name "<clinit>"))))

(defn- synthetic-javascript-method?
  "Check if method is a synthetic JavaScript method (program entry point)."
  [m]
  (= ":program" (:NAME m)))

(defn- valid-method? [m]
  (and (not (:IS_EXTERNAL m))
       (some? (:FILENAME m))
       (not= "<empty>" (:FILENAME m))
       (not (synthetic-file-method? m))
       (not (synthetic-java-method? m))
       (not (synthetic-javascript-method? m))
       (not= "<global>" (:NAME m))))

(defn- find-calls-via-ast
  "Find all CALL vertices reachable from a node via AST edges."
  [vertices edges node-id]
  (let [ast-children (->> edges
                          (filter #(and (= "AST" (:label %))
                                        (= node-id (:source %))))
                          (map :target))]
    (concat
     (for [child-id ast-children
           :let [child (get vertices child-id)]
           :when (= "CALL" (:label child))]
       child)
     (mapcat #(find-calls-via-ast vertices edges %) ast-children))))

(defn build-method-call-graph [{:keys [vertices edges]}]
  (let [all-methods (->> vertices
                         vals
                         (filter #(= "METHOD" (:label %))))
        valid-methods (->> all-methods
                           (filter valid-method?))
        method-ids (->> valid-methods
                        (map :id)
                        set)
        method-by-full-name (->> valid-methods
                                 (map (fn [m] [(:FULL_NAME m) (:id m)]))
                                 (into {}))
        ;; Try CALL edges first (Go style)
        call-to-method (->> edges
                            (filter #(and (= "CALL" (:label %))
                                          (= "CALL" (:source-label %))
                                          (= "METHOD" (:target-label %))))
                            (map (fn [{:keys [source target]}]
                                   [source target]))
                            (into {}))
        method-contains-call (->> edges
                                  (filter #(and (= "CONTAINS" (:label %))
                                                (= "METHOD" (:source-label %))
                                                (= "CALL" (:target-label %))))
                                  (map (fn [{:keys [source target]}]
                                         [source target])))
        call-edges-via-contains (->> method-contains-call
                                     (keep (fn [[method-id call-id]]
                                             (when-some [target-method-id (get call-to-method call-id)]
                                               (when (and (contains? method-ids method-id)
                                                          (contains? method-ids target-method-id))
                                                 [method-id target-method-id])))))
        ;; Fallback to AST traversal (C style) - find calls via AST edges
        call-edges-via-ast (when (empty? call-edges-via-contains)
                             (for [method valid-methods
                                   :let [method-id (:id method)
                                         calls (find-calls-via-ast vertices edges method-id)]
                                   call calls
                                   :let [target-method-id (get method-by-full-name (:METHOD_FULL_NAME call))]
                                   :when (and target-method-id
                                              (contains? method-ids target-method-id))]
                               [method-id target-method-id]))
        method-call-edges (->> (or (seq call-edges-via-contains) call-edges-via-ast)
                               distinct)]
    {:methods (->> valid-methods
                   (map (fn [m]
                          {:id (:id m)
                           :name (:NAME m)
                           :full-name (:FULL_NAME m)
                           :filename (:FILENAME m)})))
     :call-edges method-call-edges}))

(defn- graph->dgml [{:keys [methods call-edges]}]
  (let [filenames (->> methods
                       (map :filename)
                       (filter some?)
                       distinct)]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {}
                              (concat
                               (for [method methods]
                                 (xml/element ::dgml/Node
                                              (cond-> {:Id (str (:id method))
                                                       :Label (:name method)
                                                       :Category "Function"}
                                                (:full-name method) (assoc :Name (:full-name method)))))
                               (for [filename filenames]
                                 (xml/element ::dgml/Node
                                              {:Id filename
                                               :Label filename
                                               :Category "Namespace"
                                               :Group "Expanded"}))))
                 (xml/element ::dgml/Links {}
                              (concat
                               (for [[source target] call-edges]
                                 (xml/element ::dgml/Link {:Source (str source) :Target (str target)}))
                               (for [method methods
                                     :let [filename (:filename method)]
                                     :when filename]
                                 (xml/element ::dgml/Link {:Source filename
                                                           :Target (str (:id method))
                                                           :Category "Contains"}))))
                 (xml/element ::dgml/Styles {} style/styles))))

(defn extract [{:keys [input-file output-file]}]
  (let [data (json/read-str (slurp input-file) {:key-fn identity})
        parsed (parse-graphson data)
        graph (build-method-call-graph parsed)
        dgml (graph->dgml graph)]
    (sdgml/write-to-file output-file dgml)))

;; JAR-based extraction (JVM only)

(def ^:private frontend-main-classes
  {:c "io.joern.c2cpg.Main"
   :go "io.joern.gosrc2cpg.Main"
   :java "io.joern.javasrc2cpg.Main"
   :javascript "io.joern.jssrc2cpg.Main"
   :python "io.joern.pysrc2cpg.NewMain"})

(defn- invoke-main [class-name args]
  (let [cl (.getContextClassLoader (Thread/currentThread))
        clazz (Class/forName class-name true cl)
        main-method (.getMethod clazz "main" (into-array Class [(Class/forName "[Ljava.lang.String;")]))]
    (.invoke main-method nil (into-array Object [(into-array String args)]))))

(defn- parse-source
  "Parse source code using Joern frontend, creates cpg.bin file."
  [language input-path output-path]
  (let [main-class (get frontend-main-classes language)
        abs-input-path (.getCanonicalPath (java.io.File. input-path))]
    (when-not main-class
      (throw (ex-info (str "Unsupported language: " language) {:language language})))
    (invoke-main main-class [abs-input-path "-o" output-path])))

(defn- export-cpg
  "Export CPG to GraphSON format."
  [cpg-path output-dir]
  (invoke-main "io.joern.joerncli.JoernExport"
               [cpg-path
                "-o" output-dir
                "--repr" "all"
                "--format" "graphson"]))

(defn- extract-lang
  "Extract a dependency graph from source code using Joern JAR."
  [language {:keys [root-path output-file]}]
  (let [temp-dir (java.nio.file.Files/createTempDirectory
                  "joern-"
                  (into-array java.nio.file.attribute.FileAttribute []))
        temp-path (.toString temp-dir)
        cpg-path (str temp-path "/cpg.bin")
        export-dir (str temp-path "/export")]
    (try
      (parse-source language root-path cpg-path)
      (export-cpg cpg-path export-dir)
      (let [graphson-files (->> (java.io.File. export-dir)
                                (.listFiles)
                                (filter #(str/ends-with? (.getName %) ".json")))
            graphson-file (first graphson-files)
            data (json/read-str (slurp graphson-file) {:key-fn identity})
            parsed (parse-graphson data)
            graph (build-method-call-graph parsed)
            dgml (graph->dgml graph)]
        (sdgml/write-to-file output-file dgml))
      (finally
        (doseq [f (reverse (file-seq (java.io.File. temp-path)))]
          (.delete f))))))

(defn extract-go
  "Extract a dependency graph from Go source code using Joern JAR."
  [opts]
  (extract-lang :go opts))

(defn extract-c
  "Extract a dependency graph from C/C++ source code using Joern JAR."
  [opts]
  (extract-lang :c opts))

(defn extract-cpp
  "Extract a dependency graph from C++ source code using Joern JAR."
  [opts]
  (extract-lang :c opts))

(defn extract-java
  "Extract a dependency graph from Java source code using Joern JAR."
  [opts]
  (extract-lang :java opts))

(defn extract-javascript
  "Extract a dependency graph from JavaScript/TypeScript source code using Joern JAR."
  [opts]
  (extract-lang :javascript opts))

(defn extract-python
  "Extract a dependency graph from Python source code using Joern JAR."
  [opts]
  (extract-lang :python opts))

(comment
  (def input-file "experiments/joern/test/resources/joern-cpg/out-go/export.json")
  (def data (json/read-str (slurp input-file) {:key-fn identity}))

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
