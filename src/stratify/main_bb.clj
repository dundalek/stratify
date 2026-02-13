(ns stratify.main-bb
  "Babashka-specific main that delegates to JVM for unsupported commands."
  (:require
   [babashka.process :refer [process]]
   [clojure.string :as str]
   [stratify.main-clj :as main-clj]))

(defn- needs-scip-deps? [args]
  (some (fn [[arg1 arg2]]
          (and (or (= arg1 "-f") (= arg1 "--from"))
               (str/includes? arg2 "scip")))
        (partition 2 1 args)))

(defn- get-base-deps [project-path args]
  (cond-> {'org.clojure/clojure {:mvn/version "1.12.0"}
           'io.github.dundalek/stratify {:local/root (str project-path)}}
    ;; Preload scip dependencies upfront, because lazy loading using add-libs does not work with protobuf classes
    (needs-scip-deps? args)
    (assoc 'stratify/scip {:local/root (str project-path "/resources/io/github/dundalek/stratify/optional-deps/scip")})))

(defn delegate-to-jvm
  ([project-path args]
   (delegate-to-jvm project-path args nil))
  ([project-path args {:keys [extra-deps extra-env]}]
   (let [deps-map (merge (get-base-deps project-path args) extra-deps)
         ;; Specifying deps via alias and :replace-deps to prevent fetching deps from deps.edn of the analyzed project
         deps {:aliases
               {:stratify-bin
                {:replace-deps deps-map}}}
         proc (apply process {:inherit true :extra-env extra-env}
                     "clojure" "-Sdeps" (pr-str deps)
                     "-M:stratify-bin" "-m" "stratify.main" args)]
     (System/exit (:exit @proc)))))

(defn main [project-path args]
  (let [parsed (main-clj/parse-args args)]
    (when (= ::main-clj/unhandled (main-clj/run parsed))
      (delegate-to-jvm (System/getProperty "stratify.project-path") args))))
