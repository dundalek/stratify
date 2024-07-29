(ns example.main
  (:require [clojure.string :as str]))

(def a-var 1)

(def ^:private a-private-var 1)

(defmacro def-fn [& args]
  `(defn ~@args))

(def-fn my-fn [x]
  x)

(defn bar [x]
  (identity x))

(defn foo [y]
  (my-fn y)
  (str/trim (bar y)))

(defn- a-private-fn [])

(defmacro ^:private a-private-macro [])
