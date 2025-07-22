(ns rdfize.qni
  (:require
   [clojure.string :as str])
  (:import
   [java.net URLDecoder URLEncoder]
   [java.nio.charset StandardCharsets]))

(defn- strip-scheme [s]
  (if (.contains s "://")
    (subs s (+ (.indexOf s "://") 3))
    s))

(defn- encode-initial-digit [s]
  (if (and (seq s) (Character/isDigit (first s)))
    (str "%" (format "%02X" (int (first s))) (subs s 1))
    s))

(defn- decode-initial-digit [s]
  (if (and (> (count s) 2)
           (= \% (first s))
           (Character/isDigit (char (Integer/parseInt (subs s 1 3) 16))))
    (str (char (Integer/parseInt (subs s 1 3) 16)) (subs s 3))
    s))

(defn- uri->segments [uri-without-scheme]
  (let [[path-part fragment] (str/split uri-without-scheme #"#" 2)
        [authority & parts] (str/split path-part #"/")
        domain-parts (->> (str/split authority #"\.")
                          (reverse)
                          (map #(URLEncoder/encode % StandardCharsets/UTF_8)))
        path-parts (->> parts
                        (map (fn [s]
                               (-> s
                                   (URLEncoder/encode StandardCharsets/UTF_8)
                                   (str/replace "." "%2E")))))]
    (->> (cond-> domain-parts
           (seq path-parts) (concat (cons "$" path-parts))
           fragment (concat ["$$" (-> fragment
                                      (URLEncoder/encode StandardCharsets/UTF_8)
                                      (str/replace "." "%2E"))])))))

(defn uri->namespaced [uri]
  (->> (uri->segments (strip-scheme uri))
       (str/join ".")))

(defn namespaced->uri [namespaced-uri]
  (let [segments (str/split namespaced-uri #"\.")
        [domain-parts rest-parts] (split-with #(not= "$" %) segments)
        [_$ & path-and-fragment] rest-parts
        [path-parts fragment-parts] (split-with #(not= "$$" %) path-and-fragment)
        [_$$ & fragment-parts] fragment-parts
        authority (->> domain-parts
                       (reverse)
                       (map #(URLDecoder/decode % StandardCharsets/UTF_8))
                       (str/join "."))]
    (cond-> (str "http://" authority)
      (seq path-parts)
      (str "/" (->> path-parts
                    (map #(URLDecoder/decode % StandardCharsets/UTF_8))
                    (str/join "/")))
      (seq fragment-parts)
      (str "#" (->> fragment-parts
                    (map #(URLDecoder/decode % StandardCharsets/UTF_8))
                    (str/join "."))))))

(defn uri->namespaced-kw [s]
  (let [segments (uri->segments (strip-scheme s))]
    (keyword (str/join "." (butlast segments)) (encode-initial-digit (last segments)))))

(defn namespaced-kw->uri [kw]
  (namespaced->uri (str (namespace kw) "." (decode-initial-digit (name kw)))))
