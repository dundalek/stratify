(ns io.github.dundalek.stratify.report
  (:require
   [clojure.java.io :as io]
   [nextjournal.clerk :as clerk]))

;; Atom to pass paths from args to the notebook
(defonce *source-paths (atom []))

(defn report! [{:keys [source-paths output-path]}]
  (let [notebook-path (.getCanonicalPath (io/file (io/resource "io/github/dundalek/stratify/notebook.clj")))]
    (reset! *source-paths source-paths)
    (if output-path
      (clerk/build! {:index notebook-path
                     :package :single-file
                     :out-path output-path})
      (clerk/serve! {:index notebook-path
                     :browse true}))))

(comment
  (reset! *source-paths ["src"])

  (clerk/serve! {:browse true
                 :port 7788
                 :index "resources/io/github/dundalek/stratify/notebook.clj"
                 :watch-paths ["src" "resources"]}))

