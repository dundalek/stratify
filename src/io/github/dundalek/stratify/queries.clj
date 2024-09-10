(ns io.github.dundalek.stratify.queries
  (:require
   [datascript.core :as d]
   [io.github.dundalek.stratify.internal :as internal]))

(defn namespace-matcher [pattern]
  (let [re (re-pattern pattern)]
    (fn [x]
      (some? (re-matches re (str x))))))

(defn namespace-matchers [& patterns]
  (let [matchers (map namespace-matcher patterns)]
    (fn [x]
      (some #(% x) matchers))))

(defn- dissoc-nil [m]
  ;; datascript does not like when a value is nil
  (reduce-kv (fn [m k v]
               (cond-> m
                 (nil? v) (dissoc k)))
             m
             m))

(defn transact-analysis! [conn analysis]
  ;; maybe prefix the keys like :var-usage/from
  ;; then would not need extra :kind field

  ;; should combine var-usages and namespace-usages into a single thing for simpler queries?
  (d/transact! conn
               (for [item (-> analysis :namespace-definitions)]
                 (assoc item :kind :namespace)))

  (d/transact! conn
               (for [item (-> analysis :var-definitions)]
                 (assoc item :kind :var)))

  (d/transact! conn
               (for [item (-> analysis :var-usages)]
                 (dissoc-nil (assoc item :kind :var-usage))))

  (d/transact! conn
               (for [item (-> analysis :namespace-usages)]
                 (dissoc-nil (assoc item :kind :namespace-usage)))))

(defn analysis->conn [analysis]
  (let [analysis-schema {}]
    (doto (d/create-conn analysis-schema)
      (transact-analysis! analysis))))

(defn load-sources [source-paths]
  (let [result (internal/run-kondo source-paths)]
    (analysis->conn (:analysis result))))

(comment
  (def conn-valid (load-sources ["test/resources/layered-valid/src"]))
  (def conn-invalid (load-sources ["test/resources/layered-invalid/src"])))

;; Validation checks inspired by ArchUnit examples
;; https://www.archunit.org/userguide/html/000_Index.html

;; 3.2. Asserting (Architectural) Constraints

; ArchRule myRule = classes()
;     .that().resideInAPackage("..service..")
;     .should().onlyBeAccessed().byAnyPackage("..controller..", "..service..");

(defn check-architectural-constraints [conn]
  (d/q '[:find ?usage ?source-ns ?target-ns
         :in $ matches-source? matches-target?
         :where
         ; [?usage :kind :var-usage]
         ;; unamespaced :from :to both in var-usages and namespace-usages
         [?usage :from ?source-ns]
         [?usage :to ?target-ns]
         [(matches-source? ?source-ns)]
         [(matches-target? ?target-ns)]]
       @conn
       ; #(not (re-matches #".*controller.*|.*service.*" (str %)))
       ; #(re-matches #".*service.*" (str %))))
       (complement (namespace-matchers ".*controller.*" ".*service.*"))
       (namespace-matchers ".*service.*")))

(comment
  (empty? (check-architectural-constraints conn-valid))
  (empty? (check-architectural-constraints conn-invalid)))

;; 4.2. Class Dependency Checks - is there an useful example in clojure?

; classes().that().haveNameMatching(".*Bar")
;     .should().onlyHaveDependentClassesThat().haveSimpleName("Bar")

(defn check-dependencies [conn]
  (d/q '[:find ?usage ?source-var ?target-var
         :in $ matches-source? matches-target?
         :where
         [?usage :name ?target-var]
         [?usage :from-var ?source-var]
         [(matches-source? ?source-var)]
         [(matches-target? ?target-var)]]
       @conn
       (complement #(re-matches #"bar" (str %)))
       #(re-matches #".*bar" (str %))))

(comment
  (empty? (check-dependencies conn-valid))
  (empty? (check-dependencies conn-invalid)))

;; 4.6. Layer Checks

; layeredArchitecture()
;     .consideringAllDependencies()
;     .layer("Controller").definedBy("..controller..")
;     .layer("Service").definedBy("..service..")
;     .layer("Persistence").definedBy("..persistence..")
;
;     .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
;     .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
;     .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")

(defn check-layers [conn]
  (let [controller? #(re-matches #".*controller.*" (str %))
        service? #(re-matches #".*service.*" (str %))
        persistence? #(re-matches #".*persistence.*" (str %))
        usages-query '[:find ?usage ?source-ns ?target-ns
                       :in $ matches-source? matches-target?
                       :where
                       [?usage :from ?source-ns]
                       [?usage :to ?target-ns]
                       [(matches-source? ?source-ns)]
                       [(matches-target? ?target-ns)]]]
    (concat
     ;; .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
     (d/q usages-query @conn
          (constantly true)
          controller?)
     ;; .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
     (d/q usages-query @conn
          (complement controller?)
          service?)
     ;; .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
     (d/q usages-query @conn
          (complement service?)
          persistence?))))

(comment
  (empty? (check-layers conn-valid))
  (empty? (check-layers conn-invalid)))

;; Misc

(defn make-function-rule [rule-name-symbol]
  [(list rule-name-symbol '?e)
   '[?e :kind :var]
   '(or [?e :defined-by->lint-as clojure.core/defn]
        [?e :defined-by->lint-as clojure.core/defn-])])

(comment
  (def conn (load-sources ["test/resources/sample/src"]))

  ;; query namespaces
  (d/q '[:find ?name
         :in $ matches?
         :where
         [?e :kind :namespace]
         [?e :name ?name]
         [(matches? ?name)]]
       @conn
       (namespace-matcher ".*example.*")) ; "..durable.."

  ;; quey functions
  (d/q '[:find ?name
         :in $ %
         :where
         [?e :name ?name]
         (function? ?e)]
       @conn
       [(make-function-rule 'function?)]))

(comment
  (def conn (load-sources ["src"]))

  ;; imagine a rule that functions ending with `!` bang can only be depended on by functions ending with bang
  (->> (d/q '[:find ?source-var ?target-var
              :in $ matches-source? matches-target? matches-target-ns?
              :where
              [?usage :from-var ?source-var]
              [?usage :name ?target-var]
              [?usage :to ?target-ns]
              [(matches-source? ?source-var)]
              [(matches-target? ?target-var)]
              [(matches-target-ns? ?target-ns)]]
            @conn
            #(not (re-matches #".*!" (str %)))
            #(re-matches #".*!" (str %))
            (comp not #{'clojure.core 'cljs.core}))))
