(ns example.foo
  (:require [example.foo.bar :refer [y]]))

(defn x []
  (y))
