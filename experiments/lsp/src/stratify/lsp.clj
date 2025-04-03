(ns stratify.lsp
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.main :as clj-main]
   [clojure.string :as str]
   [io.github.dundalek.stratify.internal :as internal]
   [io.github.dundalek.stratify.kondo :as kondo]
   [jsonista.core :as j]
   [clojure.data.xml :as xml]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.style :as style]
   [loom.attr :as la]
   [loom.graph :as lg]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml])
  (:import
   (java.io BufferedReader InputStreamReader OutputStreamWriter)
   (java.lang ProcessHandle)))

(defn- read-message [^BufferedReader in]
  (loop [headers {}]
    (let [line (.readLine in)]
      (if (or (nil? line) (str/blank? line))
        (if (empty? headers)
          nil
          (try
            (let [content-length (Integer/parseInt (get headers "Content-Length"))
                  cbuf (char-array content-length)]
              (.read in cbuf 0 content-length)
              (String. cbuf))
            (catch Exception e
              (throw (ex-info "Failed to parse message" {:headers headers} e)))))
        (let [[k v] (str/split (str/trim line) #": " 2)]
          (recur (assoc headers k v)))))))

(defn- read-json-message [in]
  (some-> (read-message in)
          (j/read-value j/keyword-keys-object-mapper)))

(defn start-server [{:keys [args]}]
  (let [process-builder (ProcessBuilder. (into-array String args))
        process (.start process-builder)
        server-in (BufferedReader. (InputStreamReader. (.getInputStream process)))
        server-out (OutputStreamWriter. (.getOutputStream process))
        server-err (BufferedReader. (InputStreamReader. (.getErrorStream process)))
        !requests (atom {})]

    (future
      (try
        (loop []
          (when-let [message (read-json-message server-in)]
            (prn "server->client:" message)
            (when-some [id (:id message)]
              (let [p (get @!requests id)]
                (swap! !requests dissoc id)
                (deliver p (:result message))))
            (recur)))
        (catch Exception e
          (clj-main/report-error (ex-info "Error in server handler" {} e) :target "file"))))

    (future
      (io/copy server-err *err*))

    {:process process
     :in server-in
     :out server-out
     :err server-err
     :!msg-id (atom 0)
     :!requests !requests}))

(defn server-message! [server payload]
  (prn "client->server:" payload)
  (let [message (j/write-value-as-string payload)
        out ^OutputStreamWriter (:out server)]
    (locking out
      (let [content-length (count (.getBytes message "UTF-8"))]
        (.write out (str "Content-Length: " content-length "\r\n\r\n"))
        (.write out message)
        (.flush out)))))

(defn server-request-async!
  ([server method]
   (server-request-async! server method nil))
  ([server method params]
   (let [{:keys [!msg-id !requests]} server
         id (swap! !msg-id inc)
         message (cond-> {:method method
                          :jsonrpc "2.0"
                          :id id}
                   params (assoc :params params))
         p (promise)]
     (swap! !requests assoc id p)
     (server-message! server message)
     p)))

(defn server-request!
  ([server method]
   (server-request! server method nil))
  ([server method params]
   (deref (server-request-async! server method params))))

(defn server-initialize! [server opts]
  ;; TODO maybe queue other requests internally to wait on response from initialize
  (let [{:keys [root-path]} opts
        root-uri (str "file://" root-path)
        params {:capabilities {:general {:positionEncodings ["utf-16"]}
                               :workspace {:workspaceFolders true
                                           :symbol {:dynamicRegistration false
                                                    :symbolKind {:valueSet (range 1 27)}}}
                               :textDocument {:definition {:dynamicRegistration true
                                                           :linkSupport true}
                                              :references {:dynamicRegistration false}
                                              :documentSymbol {:dynamicRegistration false
                                                               :hierarchicalDocumentSymbolSupport true
                                                               :symbolKind {:valueSet (range 1 27)}}
                                              :callHierarchy {:dynamicRegistration false}
                                              :declaration {:linkSupport true}}}
                :processId (.pid (ProcessHandle/current))
                :rootPath root-path
                :rootUri root-uri
                :workDoneToken "initialize-1"
                :workspaceFolders [{:name root-path
                                    :uri root-uri}]}]
                ; :clientInfo {:name "lsp-sniffer" :version "0.1.0"}
                ; :initializationOptions {}
                ; :trace "off"

    (server-request! server "initialize" params)
    ; (server-request-async! server "initialize" params)))
    (server-message! server {:method "initialized" :params {} :jsonrpc "2.0"})))

(defn server-stop! [server]
  (server-request! server "shutdown")
  (server-message! server {:method "exit" :jsonrpc "2.0"}))

(defn path->uri [path]
  (str "file://" path))

(defn location-less-or-equal? [a b]
  (or (< (:line a) (:line b))
      (and (= (:line a) (:line b))
           (<= (:character a) (:character b)))))

(comment
  [(true? (location-less-or-equal? {:line 0 :character 0} {:line 0 :character 15}))
   (true? (location-less-or-equal? {:line 0 :character 15} {:line 1 :character 1}))
   (false? (location-less-or-equal? {:line 1 :character 15} {:line 1 :character 1}))
   (false? (location-less-or-equal? {:line 1 :character 15} {:line 0 :character 1}))])

(defn range-contains? [outer inner]
  (and (location-less-or-equal? (:start outer) (:start inner))
       (location-less-or-equal? (:end inner) (:end outer))))

(defn symbol->id [{:keys [uri sym]}]
  (let [{:keys [start end]} (:selectionRange sym)]
    (str uri "#L" (:line start) "C" (:character start) "-L" (:line end) "C" (:character end))))

(defn ->dgml [{:keys [root-path source-paths]}]
  (let [server (start-server {:args ["clojure-lsp"]})
        uri-base (str "file://" root-path "/")
        _ (server-initialize! server {:root-path root-path})
        file-uris (->> source-paths
                       (mapcat (fn [path]
                                 (fs/glob (fs/file root-path path) "**.clj{,c,s}")))
                       (map path->uri))
        file-uris-set (set file-uris)
        symbols (->> file-uris
                     (mapcat (fn [uri]
                               (->> (server-request! server "textDocument/documentSymbol"
                                                     {:textDocument {:uri uri}
                                                      :position {:character 0, :line 0}})
                                    (map (fn [sym]
                                           {:uri uri
                                            :sym sym}))))))
        symbols-by-uri (->> symbols (group-by :uri))
        symbol-references (->> symbols
                               (map (fn [item]
                                      (let [{:keys [uri sym]} item
                                            position (-> sym :selectionRange :start)]
                                        (assoc item :references
                                               (server-request! server "textDocument/references"
                                                                {:textDocument {:uri uri}
                                                                 :context {:includeDeclaration false} ; if it is from a document symbol we don't need the declaration
                                                                 :position position})))))
                               (doall))
        resolved-references (->> symbol-references
                                 (mapcat (fn [{:keys [references] target-uri :uri target-sym :sym}]
                                           (->> references
                                                (filter (fn [{source-uri :uri}]
                                                          (contains? file-uris-set source-uri)))
                                                (map (fn [{source-uri :uri source-range :range}]
                                                       (let [source-sym (->> (get symbols-by-uri source-uri)
                                                                             (filter #(range-contains? (-> % :sym :range) source-range))
                                                                             (first))]
                                                         [(or source-sym {:uri source-uri})
                                                          {:uri target-uri :sym target-sym}])))))))
        links (->> resolved-references
                   (map (fn [[source target]]
                          [(if (:sym source)
                             (symbol->id source)
                             (:uri source))
                           (symbol->id target)])))
        nodes (->> symbols
                   (map (fn [item]
                          (let [{:keys [uri sym]} item]
                            {:id (if sym (symbol->id item) uri)
                             :label (:name sym)
                             :parent uri}))))
                            ;; kind map to category
                            ; :kind (:kind sym)}))))]

        g (-> (lg/digraph)
              (lg/add-nodes* file-uris)
              (internal/add-clustered-namespace-hierarchy-path-based uri-base))
        namespace-with-nested-namespace? (->> (lg/nodes g)
                                              (map #(la/attr g % :parent))
                                              set)]
    (server-stop! server)
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {}
                              (concat
                               (for [node-id (lg/nodes g)]
                                 (xml/element ::dgml/Node
                                              {:Id node-id
                                               :Label (la/attr g node-id :label)
                                               :Category "Namespace"
                                               :Group (if (namespace-with-nested-namespace? node-id) "Expanded" "Collapsed")}))
                               (for [{:keys [id label]} nodes]
                                 (xml/element ::dgml/Node
                                              {:Id id
                                               :Label label}))))
                 (xml/element ::dgml/Links {}
                              (concat
                               (->> nodes
                                    (keep (fn [{:keys [id parent]}]
                                            (when parent
                                              (xml/element ::dgml/Link {:Source parent :Target id :Category "Contains"})))))
                               (->> (lg/nodes g)
                                    (keep (fn [node-id]
                                            (when-some [parent (la/attr g node-id :parent)]
                                              (xml/element ::dgml/Link {:Source parent :Target node-id :Category "Contains"})))))
                               (for [[source target] links]
                                 (xml/element ::dgml/Link {:Source source :Target target}))))
                 (xml/element ::dgml/Styles {} style/styles))))

(comment
  (let [data (->dgml {:root-path (.getCanonicalPath (io/file "../../test/resources/nested"))
                      :source-paths ["src"]})]
    (sdgml/write-to-file "../../../../shared/lsp-nested.dgml" data))

  (let [data (->dgml {:root-path (.getCanonicalPath (io/file "../.."))
                      :source-paths ["src"]})]
    (sdgml/write-to-file "../../../../shared/lsp-stratify.dgml" data))

  (def analysis (kondo/analysis ["../../test/resources/nested"]))

  (kondo/->graph analysis)
  (internal/analysis->graph {:analysis analysis}))

(comment
  (def root-path (.getCanonicalPath (io/file "../..")))
  (def root-path (.getCanonicalPath (io/file "../../test/resources/nested")))
  (def uri-base (str "file://" root-path "/"))

  (def server (start-server {:args ["clojure-lsp"]}))

  (tap> (server-initialize! server {:root-path root-path}))

  (server-stop! server)

  (let [uri (str uri-base "src/stratify/main.clj")]
    (->> (server-request! server "textDocument/documentSymbol"
                          {:position {:character 0, :line 0}, :textDocument {:uri uri}})
         #_(mapv (fn [document-symbol]
                   (update document-symbol :kind symbol-kind-lookup)))
         #_(mapv (fn [{:keys [name kind selectionRange]}]
                   [(str/replace-first uri base-path "") name kind selectionRange]))
         #_(mapcat (fn [{:keys [selectionRange]}]
                     (->> (server-request! server "textDocument/references"
                                           {:textDocument {:uri uri}, :context {:includeDeclaration true}, :position (:start selectionRange)})
                          (mapv (fn [{:keys [uri range]}]
                                  [(str/replace-first uri base-path "") range])))))

         tap>))

  (let [uri (str uri-base "src/stratify/main.clj")]
    (->> (server-request! server "textDocument/documentSymbol"
                          {:position {:character 0, :line 0}, :textDocument {:uri uri}})))
         ; (butlast)
         ; last))

  (let [uri (str uri-base "src/example/foo.clj")]
    (->> (server-request! server "textDocument/documentSymbol"
                          {:position {:character 0, :line 0}, :textDocument {:uri uri}})))

  (->> (server-request! server "textDocument/references"
                        {:textDocument {:uri (str uri-base "src/stratify/main.clj")}
                         :context {:includeDeclaration false} ; if it is from a document symbol we don't need the declaration
                         :position {:character 13, :line 57}}))
      ; (tap>))

  (->> (server-request! server "workspace/symbol"
                        {:query ""})
      ; kind
      ; location - uri, range
      ; name)
       (tap>))

  (def hierarchy-item
    (->> (server-request! server "textDocument/prepareCallHierarchy"
                          {:textDocument {:uri (str uri-base "src/stratify/main.clj")}
                           :position {:character 13, :line 57}
                           :workDoneToken "hierarchy-1"})
         first))

  (server-request! server "callHierarchy/incomingCalls"
                   {:item hierarchy-item})

  (server-request! server "callHierarchy/outgoingCalls"
                   {:item hierarchy-item}))
