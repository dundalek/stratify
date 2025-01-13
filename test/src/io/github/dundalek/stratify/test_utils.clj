(ns io.github.dundalek.stratify.test-utils
  (:require
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]
   [clojure.test :refer [is testing]]))

(defn is-same? [path]
  (let [result (sh "git" "status" "--porcelain" path)]
    (testing path
      (is (= 0 (:exit result)))
      (is (= "" (:err result)))
      (is (= [""] (str/split-lines (:out result)))))))

(defmacro thrown-error-code [& body]
  `(try
     ~@body
     nil
     (catch clojure.lang.ExceptionInfo e#
       (:code (ex-data e#)))))
