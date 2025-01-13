(ns io.github.dundalek.stratify.error-catalog-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.internal :as internal]
   [io.github.dundalek.stratify.test-utils :as tu]
   [stratify.main :as main]))

(def ^:dynamic *writer* nil)

(defn report-with-stripped-tmp-file [t]
  ;; Report file name is randomly generated and ends up being different on every run.
  ;; This helper strips it so that error messages can be deterministically compared in tests.
  (-> (with-out-str
        (binding [*err* *out*]
          (main/report-error t)))
      (str/replace #"(?s)Full report at:.*$" "Full report at:\n...\n")
      (print)))

(defn print-catalog-error [code t]
  (binding [*out* *writer*]
    (println)
    (println (str "## " code))
    (println)
    (println "```")
    (report-with-stripped-tmp-file t)
    (println "```")))

(defmacro test-error-code [code & body]
  `(try
     ~@body
     (is false "did not throw")
     (catch Throwable t#
       (is (= ~code (:code (ex-data t#))))
       (print-catalog-error ~code t#))))

(def catalog-file
  "doc/error-catalog.md")

(deftest catalog
  (with-open [w (io/writer catalog-file)]
    (binding [*writer* w]
      (print-catalog-error "Unknown error" (Error. "Sample message"))

      (test-error-code
       ::internal/no-source-namespaces
       (main/main* "NON_EXISTING"))))

  (tu/is-same? catalog-file))
