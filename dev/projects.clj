(ns projects
  (:require
   [io.github.dundalek.stratify.internal :as stratify]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]))

(def projects-dir "target/projects")

(def clojure-projects
  [#_"https://github.com/CircleCI-Archived/frontend"
   "https://github.com/HumbleUI/HumbleUI"
   "https://github.com/LightTable/LightTable"
   "https://github.com/babashka/babashka"
   "https://github.com/babashka/sci"
   "https://github.com/clj-kondo/clj-kondo"
   "https://github.com/cljdoc/cljdoc"
   "https://github.com/cljfx/cljfx"
   "https://github.com/clojure-lsp/clojure-lsp"
   "https://github.com/clojure/clojure"
   "https://github.com/day8/re-frame"
   "https://github.com/dharrigan/startrek"
   "https://github.com/district0x/ethlance"
   "https://github.com/djblue/portal"
   "https://github.com/fractl-io/fractl"
   "https://github.com/fulcrologic/fulcro"
   "https://github.com/funcool/promesa"
   "https://github.com/furkan3ayraktar/clojure-polylith-realworld-example-app"
   "https://github.com/hyperfiddle/electric"
   "https://github.com/jepsen-io/jepsen"
   "https://github.com/juji-io/datalevin"
   "https://github.com/lambdaisland/kaocha"
   "https://github.com/logseq/logseq"
   "https://github.com/metabase/metabase"
   "https://github.com/metosin/malli"
   "https://github.com/metosin/reitit"
   "https://github.com/mogenslund/liquid"
   "https://github.com/nextjournal/clerk"
   "https://github.com/onyx-platform/onyx"
   "https://github.com/overtone/overtone"
   "https://github.com/penpot/penpot"
   "https://github.com/phronmophobic/membrane"
   "https://github.com/quoll/asami"
   "https://github.com/re-path/studio"
   "https://github.com/reagent-project/reagent"
   "https://github.com/replikativ/datahike"
   "https://github.com/riemann/riemann"
   "https://github.com/ring-clojure/ring"
   "https://github.com/status-im/status-mobile"
   "https://github.com/thheller/shadow-cljs"
   "https://github.com/tonsky/datascript"
   "https://github.com/unclebob/more-speech"
   "https://github.com/vlaaad/reveal"
   "https://github.com/walmartlabs/lacinia"
   "https://github.com/wardle/hermes"
   "https://github.com/wardle/pc4"
   "https://github.com/wilkerlucio/pathom"
   "https://github.com/wotbrew/relic/"
   "https://github.com/xtdb/xtdb"
   "https://github.com/zerg000000/clojure-petclinic"])

(defn clone-projects []
  ; (fs/create-dirs projects-dir)

  (doseq [repo clojure-projects]
    (println "Cloning:" repo)
    (try
      (sh "git" "clone" repo :dir projects-dir)
      (catch Exception e
        (println "Failed to clone:" repo "Error:" (ex-message e))))))

(defn subpath [parent-dir child-path]
  (.getPath (io/file parent-dir child-path)))

(defn project-sources [project-dir]
  (case (.getName project-dir)
    "clojure-lsp" (set (map (partial subpath project-dir) ["lib/src" "cli/src"]))
    "clojure-polylith-realworld-example-app" (concat (->> (.listFiles (io/file project-dir "bases"))
                                                          (map #(subpath % "src")))
                                                     (->> (.listFiles (io/file project-dir "components"))
                                                          (map #(subpath % "src"))))
    "frontend" (set (map (partial subpath project-dir) ["src-cljs/frontend"]))
    "jepsen" (set (map (partial subpath project-dir) ["jepsen/src"]))
    "penpot" (set (map (partial subpath project-dir) ["backend/src" "common/src" "exporter/src" "frontend/src"]))
    "reitit" (->> (.listFiles (io/file project-dir "modules"))
                  (map #(subpath % "src"))
                  (set))
    "ring" (set (map (partial subpath project-dir) [#_"ring-bench/src" "ring-core/src" #_"ring-devel/src" "ring-jetty-adapter/src" "ring-servlet/src"]))
    "xtdb" (->> (.listFiles (io/file project-dir "modules"))
                (map #(subpath % "src"))
                (into #{(subpath project-dir "core/src")}))
    "pc4" (->> (.listFiles project-dir)
               (map #(subpath % "src"))
               (filter #(.isDirectory (io/file %))))
    "ethlance" (set (map (partial subpath project-dir) ["server/src" "shared/src" "ui/src"]))

    ;; Otherwise fallback to src/
    #{(subpath project-dir "src")}))

(defn extract-project [project-name-or-dir]
  (let [[project-name project-dir]
        (if (string? project-name-or-dir)
          [project-name-or-dir (io/file projects-dir project-name-or-dir)]
          [(.getName project-name-or-dir) project-name-or-dir])]
    (stratify/extract
     {:source-paths (project-sources project-dir)
      :output-file (str "target/dgml/" project-name ".dgml")})))
      ; :flat-namespaces true})))

(comment
  (clone-projects)

  (extract-project "kaocha")
  (extract-project "promesa")

  (doseq [project-dir (.listFiles (io/file projects-dir))]
    (when (not= (.getName project-dir) "penpot")
      (println "Extracting:" (.getName project-dir))
      (extract-project project-dir)))

  (->> (for [project-dir (.listFiles (io/file projects-dir))]
         [(.getName project-dir)
          (set (project-sources project-dir))])
       (into {}))

  (stratify/extract
   {:source-paths ["/home/me/code/dinodoc/dinodoc/components/antora/src"
                   "/home/me/code/dinodoc/dinodoc/components/dokka/src"
                   "/home/me/code/dinodoc/dinodoc/components/structurizr/src"
                   "/home/me/code/dinodoc/dinodoc/components/contextmapper/src"
                   "/home/me/code/dinodoc/dinodoc/components/statecharts/src"
                   "/home/me/code/dinodoc/dinodoc/src"]
    :output-file "target/dgml/dinodoc.dgml"})

  (stratify/extract
   {:source-paths ["/home/me/projects/daba/components/core/src"]
    :output-file "target/dgml/daba.dgml"}))
