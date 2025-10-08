(ns example2)

(defn x [])

(defn y [])

(defn e [])

(defn b []
  (x))

(defn a []
  (b))

(defn c []
  (y)
  (x))

(defn d []
  (y)
  (e))
