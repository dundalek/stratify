(ns io.github.dundalek.stratify.pulumi-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.pulumi :as pulumi]
   [io.github.dundalek.stratify.test-utils :refer [is-same?]]
   [stratify.main :as main]))

(defn test-extraction [file-prefix]
  (let [output-file (str file-prefix ".dgml")]
    (pulumi/extract {:input-file (str file-prefix ".json")
                     :output-file output-file})
    (is-same? output-file)))

(deftest sample-via-cli
  (let [file-prefix "test/resources/pulumi/sample-export"
        output-file (str file-prefix ".dgml")]
    (is (= (slurp output-file)
           (with-out-str
             (main/-main "-f" "pulumi" (str file-prefix ".json")))))))

(deftest sample-export
  (test-extraction "test/resources/pulumi/sample-export"))

(deftest sample-previews
  (test-extraction "test/resources/pulumi/sample-preview-creates")
  (test-extraction "test/resources/pulumi/sample-preview-update")

  ;; Since the update only changes lambda body, we can assert that results are the same
  (is (= (str/split-lines (slurp "test/resources/pulumi/sample-preview-creates.dgml"))
         (str/split-lines (slurp "test/resources/pulumi/sample-preview-update.dgml")))))

(deftest sample-preview-deletes
  (test-extraction "test/resources/pulumi/sample-preview-deletes"))
