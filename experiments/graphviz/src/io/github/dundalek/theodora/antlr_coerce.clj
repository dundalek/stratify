;; Based on https://github.com/aphyr/clj-antlr/blob/master/src/clj_antlr/coerce.clj
(ns io.github.dundalek.theodora.antlr-coerce
  (:require
   [clj-antlr.coerce :as coerce]
   [clj-antlr.common :as c])
  (:import
   (org.antlr.v4.runtime Parser ParserRuleContext)
   (org.antlr.v4.runtime.tree ParseTree TerminalNode)
   (org.antlr.v4.tool Grammar)))

;; Workarounds to access private vars
(def sexpr-head @#'coerce/sexpr-head)
(def maybe-error-node @#'coerce/maybe-error-node)
(def attach-positional-metadata @#'coerce/attach-positional-metadata)

(defn terminal->sexpr [^TerminalNode node ^Parser p]
  (let [t (.getSymbol node)
        text (.getText t)]
    (if-some [symbolic-name (.getSymbolicName (.getVocabulary p) (.getType t))]
      (list (clj-antlr.common/fast-keyword symbolic-name) text)
      text)))

(defn sexpr [^ParseTree t ^Parser p ^Grammar g opts]
  (if (instance? ParserRuleContext t)
    (->> (mapv #(sexpr % p g opts) (c/children t))
         (cons (sexpr-head t p g opts))
         (maybe-error-node t)
         (attach-positional-metadata t))
    (do
      (assert (instance? TerminalNode t))
      ;; Replacing (literal->sexpr t)
      (terminal->sexpr t p))))

(defn tree->sexpr
  "Takes a map with a :tree node, a :parser (required for interpreting the
  indices of rule nodes as rule names), and a :grammar (required for interpreting
  alternate production names), and returns a lazily evaluated tree,
  where each tree is either a string, or a sequence composed of a rule name
  followed by that rule's child trees. For instance:

  (:json (:object \"{\" (:pair \"age\" \":\" (:value \"53\"))))"
  [m]
  (vary-meta (sexpr (:tree m) (:parser m) (:grammar m) (:opts m))
             assoc :errors (:errors m)))
