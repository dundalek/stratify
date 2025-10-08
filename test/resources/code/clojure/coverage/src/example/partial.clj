(ns example.partial)

(defn covered [x]
  (+ x 1))

(defn uncovered [x]
  (+ x 1))

(defn part [x]
  (if (= x 1)
    (+ x 1)
    (str "uncovered: " x)))

(defn yellow [x]
  (some-> x (+ 1)))
