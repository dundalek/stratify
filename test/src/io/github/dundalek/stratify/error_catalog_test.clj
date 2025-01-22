(ns io.github.dundalek.stratify.error-catalog-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.codecharta :as codecharta]
   [io.github.dundalek.stratify.codecov :as codecov]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.graphviz :as graphviz]
   [io.github.dundalek.stratify.internal :as internal]
   [io.github.dundalek.stratify.pulumi :as pulumi]
   [io.github.dundalek.stratify.test-utils :as tu]
   [stratify.main :as main]))

(def catalog-file "doc/error-catalog.md")

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
    (println (str "### " code))
    (println)
    (println "```")
    (report-with-stripped-tmp-file t)
    (println "```")))

(defn print-category-heading [heading]
  (binding [*out* *writer*]
    (println)
    (println (str "## " heading))
    (println)))

(defmacro test-error-code [expected-code & body]
  `(try
     ~@body
     (is false "did not throw")
     (catch Throwable t#
       (let [actual-code# (:code (ex-data t#))]
         (is (= ~expected-code actual-code#))
         (print-catalog-error (or ~expected-code actual-code#) t#)))))

(deftest catalog
  (with-open [w (io/writer catalog-file)]
    (binding [*writer* w]
      (print-catalog-error "Unknown error" (Error. "Sample message"))

      (test-error-code
       ::internal/no-source-namespaces
       (main/main* "NON_EXISTING"))

      (test-error-code
       ::sdgml/failed-to-write
       (main/main* "-o" "/output.dgml" "test/resources/sample/src"))

      (print-category-heading "codecharta")

      (test-error-code
       ::codecharta/ccsh-not-found
       (binding [codecharta/*ccsh-bin* "NON_EXISTING"]
         (main/main* "-t" "codecharta" "test/resources/nested/src")))

      (test-error-code
       ::codecharta/ccsh-failed-to-run
       ;; Non-executable
       (binding [codecharta/*ccsh-bin* "./LICENSE"]
         (main/main* "-t" "codecharta" "test/resources/nested/src")))

      (print-category-heading "codecov")

      (test-error-code
       ::codecov/failed-to-parse
       (main/main* "--coverage-file" "test/resources/pulumi/NON_EXISTING.json" "test/resources/coverage/src"))

      (test-error-code
       ::codecov/failed-to-parse
       (main/main* "--coverage-file" "test/resources/pulumi/bad.json" "test/resources/coverage/src"))

      (test-error-code
       ::codecov/invalid-input
       (main/main* "--coverage-file" "test/resources/pulumi/empty.json" "test/resources/coverage/src"))

      (test-error-code
       :io.github.dundalek.stratify.codecov/coverage-range-out-of-bounds
       (main/main* "--coverage-file" "test/resources/codecov/empty-coverage.json" "test/resources/coverage/src"))

      (print-category-heading "graphviz")

      (test-error-code
       ::graphviz/failed-to-parse
       (main/main* "-f" "dot" "test/resources/graphviz/bad.dot"))

      (test-error-code
       ::graphviz/failed-to-parse
       (main/main* "-f" "dot" "test/resources/graphviz/NON_EXISTING"))

      (test-error-code
       ::graphviz/empty-graph
       (main/main* "-f" "dot" "test/resources/graphviz/empty.dot"))

      (test-error-code
       ::sdgml/failed-to-write
       (main/main* "-f" "dot" "-o" "/tmp/__NON_EXISTING_DIRECTORY_42__/output.dgml" "test/resources/graphviz/simple.dot"))

      (print-category-heading "overarch")

      (test-error-code
       nil
       (main/main* "-f" "overarch" "test/resources/overarch/invalid.edn"))

      (test-error-code
       ::sdgml/failed-to-write
       (main/main* "-f" "overarch" "-o" "/output.dgml" "test/resources/overarch/nodel.edn"))

      (print-category-heading "pulumi")

      (test-error-code
       ::pulumi/failed-to-parse
       (main/main* "-f" "pulumi" "test/resources/pulumi/bad.json"))

      (test-error-code
       ::pulumi/invalid-input
       (main/main* "-f" "pulumi" "test/resources/pulumi/empty.json"))

      (test-error-code
       ::sdgml/failed-to-write
       (main/main* "-f" "pulumi" "-o" "/output.dgml" "test/resources/pulumi/sample-export.json"))))

  (tu/is-same? catalog-file))
