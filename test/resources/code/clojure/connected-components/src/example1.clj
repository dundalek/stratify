(ns example1)

(defn x [])

(defn y [])

(defn e [])

(defn b []
  (x))

(defn a []
  (b))

(defn c []
  (y))

(defn d []
  (y)
  (e))
