(ns io.github.dundalek.stratify.dgml
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]))

(defn write-to-file [output-file dgml-data]
  (try
    (if (instance? java.io.Writer output-file)
      (xml/indent dgml-data output-file)
      (with-open [out (io/writer output-file)]
        (xml/indent dgml-data out)))
    (catch Throwable t
      (throw (ex-info "Failed to write output file." {:code ::failed-to-write} t)))))
