(ns io.github.dundalek.stratify.report
  (:require
   [clojure.java.io :as io]
   [nextjournal.clerk :as clerk]))

;; Atom to pass paths from args to the notebook
(defonce *source-paths (atom []))

(defn report! [{:keys [source-paths output-path notebook-path]}]
  (let [notebook-path (.getCanonicalPath (io/file (io/resource notebook-path)))]
    (reset! *source-paths source-paths)
    (if output-path
      (clerk/build! {:index notebook-path
                     :package :single-file
                     :out-path output-path})
      (clerk/serve! {:index notebook-path
                     :browse true}))))

(comment
  (reset! *source-paths ["src"])
  (reset! *source-paths ["test/resources/nested/src"])
  (reset! *source-paths ["test/resources/connected-components/src"])

  (clerk/serve! {:browse true
                 :port 7788
                 :index "resources/io/github/dundalek/stratify/notebook.clj"
                 :watch-paths ["src" "resources"]})

  (clerk/halt!)

  (reset! *source-paths ["test/resources/nested/output-default.dgml"])
  (reset! *source-paths ["stratify.dgml"]))
