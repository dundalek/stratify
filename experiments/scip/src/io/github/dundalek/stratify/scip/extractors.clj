(ns io.github.dundalek.stratify.scip.extractors
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]))

(defn extract-go [{:keys [dir args output-file]}]
  (apply shell {:dir dir}
         "go run github.com/sourcegraph/scip-go/cmd/scip-go@latest"
         "--output" (fs/absolutize output-file)
         args))

(defn extract-py [{:keys [dir args output-file]}]
  (apply shell {:dir dir}
         "npm exec --package=@sourcegraph/scip-python -y -- scip-python index"
         "--output" (fs/absolutize output-file)
         args))

(defn extract-rb [{:keys [dir args output-file]}]
  (apply shell {:dir dir}
         "bundle exec scip-ruby "
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
         "npm exec --package=@sourcegraph/scip-typescript -y -- scip-typescript index"
         "--output" (fs/absolutize output-file)
         args))
