(ns io.github.dundalek.theodora.parser
  (:require
   [clj-antlr.core :as antlr]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [dorothy.core :as-alias dc]
   [io.github.dundalek.theodora.antlr-coerce :as helper]))

(defn make-parser []
  (antlr/parser
   (slurp (io/resource "DOT.g4"))
   ;; Using monkey-patched variation of clj-antlr.coerce/tree->sexpr to keep tags for string terminals.
   {:format helper/tree->sexpr}))

(defn terminal-matches? [token kw]
  (and (seq? token)
       (= kw (first token))))

(defn unescape-string [s]
  (assert (<= 2 (count s)))
  (-> s
      (subs 1 (dec (count s)))
      (str/replace #"\\\"" "\"")
      (str/replace #"\\\\" "\\\\")))

(defmulti transform first)

(defmethod transform :ID [[_ id]]
  id)

(defmethod transform :STRING [[_ id]]
  (unescape-string id))

(defmethod transform :NUMBER [[_ id]]
  (or (parse-long id)
      (parse-double id)))

(defmethod transform :HTML_STRING [[_ id]]
  ;; Strip opening and closing angle brackets
  (assert (<= 2 (count id)))
  (subs id 1 (dec (count id))))

(defmethod transform :id_ [[_ id]]
  (transform id))

(defmethod transform :node_id [[_ id port-token]]
  (let [node {:type ::dc/node-id
              :id (transform id)}]
    (if-some [[_ _colon port _colon compass-pt] port-token]
      (assoc node
             :port (some-> port transform)
             :compass-pt (some-> compass-pt transform))
      node)))

(defmethod transform :a_list [[_ & a-lists]]
  (->> a-lists
       (filter #(terminal-matches? % :id_))
       (partition 2)
       (reduce (fn [m [k v]]
                 (assoc m (transform k) (transform v)))
               {})))

(defmethod transform :attr_list [[_ & a-lists]]
  (->> a-lists
       (partition 3)
       (map (fn [[_left-bracket a-list _right-bracket]]
              (transform a-list)))
       (reduce merge {})))

(defmethod transform :node_stmt [[_ id attr-list]]
  {:type ::dc/node
   :id (transform id)
   :attrs (if (some? attr-list)
            (transform attr-list)
            {})})

(defmethod transform :edgeRHS [[_ & rhs]]
  (->> rhs
       (partition 2)
       (map (fn [[_edgeop target]]
              ;; dorothy does not represent edgeop, it uses :edge-op globally on *options* based on if the type is graph or digraph
              ;; might need to revisit this
              (transform target)))))

(defmethod transform :edge_stmt [[_ lhs rhs attr-list]]
  {:type ::dc/edge
   :node-ids (into [(transform lhs)]
                   (transform rhs))
   :attrs (if (some? attr-list)
            (transform attr-list)
            {})})

(defmethod transform :attr_stmt [[_ kind attr-list]]
  (assert (seq? kind))
  {:type (case (first kind)
           :GRAPH ::dc/graph-attrs
           :NODE ::dc/node-attrs
           :EDGE ::dc/edge-attrs)
   :attrs (if (some? attr-list)
            (transform attr-list)
            {})})

(defmethod transform :subgraph [[_ & tokens]]
  {:type ::dc/subgraph
   :id (some->> tokens
                (filter #(terminal-matches? % :id_))
                (first)
                (transform))
   :statements (->> tokens
                    (filter #(terminal-matches? % :stmt_list))
                    (first)
                    (transform))})

(defmethod transform :stmt [[_ & nodes]]
  (if (= (count nodes) 1)
    (transform (first nodes))
    ;; `ID '=' ID` statement is just shorthand for graph attrs
    (let [[k op v] nodes]
      (assert (= (count nodes) 3))
      (assert (= op "="))
      {:type ::dc/graph-attrs
       :attrs {(transform k) (transform v)}})))

(defmethod transform :stmt_list [[_ & statements]]
  (->> statements
       ;; filter out primitive string literals like `;`
       (filter seq?)
       (mapv transform)))

(defmethod transform :graph [[_ & tokens]]
  (let [strict? (terminal-matches? (first tokens) :STRICT)
        tokens (cond-> tokens strict? next)
        graph-type (cond
                     (terminal-matches? (first tokens) :GRAPH) ::dc/graph
                     (terminal-matches? (first tokens) :DIGRAPH) ::dc/digraph
                     :else (assert false "Grammar should not produce other graph type."))
        tokens (next tokens)
        [id tokens] (if (seq? (first tokens))
                      [(transform (first tokens)) (next tokens)]
                      [nil tokens])
        ;; skip "{" literal
        tokens (next tokens)
        statements (transform (first tokens))]
    {:type graph-type
     :id id
     :strict? strict?
     :statements statements}))

(def !parser
  (delay (make-parser)))

(defn parse [input]
  (-> input
      (@!parser)
      (transform)))
