(ns example.dependencies)

(defn foo-bar [])

(defn bar []
  (foo-bar))

(defn other  []
  (foo-bar))
