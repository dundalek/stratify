(ns io.github.dundalek.stratify.scip-extractors
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]))

(defn extract-go [{:keys [dir args output-file]}]
  (apply shell {:dir dir}
         "scip-go"
         "--output" (fs/absolutize output-file)
         args))

(defn extract-py [{:keys [dir args output-file]}]
  (apply shell {:dir dir}
         "scip-python index"
         "--output" (fs/absolutize output-file)
         args))

(defn extract-rb [{:keys [dir args output-file]}]
  (apply shell {:dir dir}
         "scip-ruby"
         "--index-file" (fs/absolutize output-file)
         args))

;; requires both rust-analyzer and cargo
(defn extract-rs [{:keys [dir args output-file]}]
  (apply shell {:dir dir}
         "rust-analyzer scip ."
         "--output" (fs/absolutize output-file)
         args))

(defn extract-ts [{:keys [dir args output-file]}]
  (apply shell {:dir dir}
         "scip-typescript index"
         "--output" (fs/absolutize output-file)
         args))
