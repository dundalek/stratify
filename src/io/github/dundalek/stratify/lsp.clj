(ns io.github.dundalek.stratify.lsp
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.main :as clj-main]
   [clojure.string :as str]
   [io.github.dundalek.stratify.dgml :as sdgml]
   [io.github.dundalek.stratify.internal :as internal]
   [io.github.dundalek.stratify.kondo :as kondo]
   [io.github.dundalek.stratify.style :as style]
   [jsonista.core :as j]
   [loom.attr :as la]
   [loom.graph :as lg]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml])
  (:import
   (java.io BufferedReader InputStreamReader OutputStreamWriter)
   (java.lang ProcessHandle)
   (java.util.concurrent TimeUnit)))

(def symbol-kind
  {::file 1
   ::module 2
   ::namespace 3
   ::package 4
   ::class 5
   ::method 6
   ::property 7
   ::field 8
   ::constructor 9
   ::enum 10
   ::interface 11
   ::function 12
   ::variable 13
   ::constant 14
   ::string 15
   ::number 16
   ::boolean 17
   ::array 18
   ::object 19
   ::key 20
   ::null 21
   ::enum-member 22
   ::struct 23
   ::event 24
   ::operator 25
   ::type-parameter 26})

(def symbol-kind-lookup
  (into {} (map (juxt val key)) symbol-kind))

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

(defn server-message! [server payload]
  (prn "client->server:" payload)
  (let [message (j/write-value-as-string payload)
        out ^OutputStreamWriter (::out server)]
    (locking out
      (let [content-length (count (.getBytes message "UTF-8"))]
        (.write out (str "Content-Length: " content-length "\r\n\r\n"))
        (.write out message)
        (.flush out)))))

(defn start-server [{:keys [args]}]
  (let [process-builder (ProcessBuilder. (into-array String args))
        process (.start process-builder)
        server-in (BufferedReader. (InputStreamReader. (.getInputStream process)))
        server-out (OutputStreamWriter. (.getOutputStream process))
        server-err (BufferedReader. (InputStreamReader. (.getErrorStream process)))
        !requests (atom {})
        !progresses (atom {})
        !initialization (atom nil)
        server {::process process
                ::in server-in
                ::out server-out
                ::err server-err
                ::!msg-id (atom 0)
                ::!requests !requests
                ::!progresses !progresses
                ::!initialization !initialization}]

    (future
      (try
        (loop []
          (when-let [message (read-json-message server-in)]
            (prn "server->client:" message)
            (cond
              ;; To treat message from server as response it must not have :method, which is for requests that the server initiates, e.g. "window/workDoneProgress/create"
              (and (not (contains? message :method)) (:id message))
              (let [{:keys [id]} message
                    p (get @!requests id)]
                (swap! !requests dissoc id)
                (deliver p (:result message)))

              (= (:method message) "window/workDoneProgress/create")
              (let [{:keys [id]} message]
                (swap! !progresses assoc (-> message :params :token) true)
                (server-message! server {:jsonrpc "2.0"
                                         :result nil
                                         :id id}))

              (and (= (:method message) "$/progress")
                   (= (-> message :params :value :kind) "end"))
              ;; could also consider keeping and updating the progress text
              (swap! !progresses dissoc (-> message :params :token))

              (= (:method message) "workspace/configuration")
              (let [{:keys [id]} message]
                ;; no configuration support for now, but respond back to the server to avoid getting stuck
                (server-message! server {:jsonrpc "2.0"
                                         :result [nil]
                                         :id id})))

            (recur)))
        (catch Exception e
          (clj-main/report-error (ex-info "Error in server handler" {} e) :target "file"))))

    (future
      (io/copy server-err *err*))

    server))

(defn server-request-async!
  ([server method]
   (server-request-async! server method nil))
  ([server method params]
   (let [{::keys [!msg-id !requests]} server
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

(defn path->uri [path]
  (str "file://" path))

(def ^:private default-client-capabilities
  {:general {:positionEncodings ["utf-16"]}
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
                  :declaration {:linkSupport true}}
   :window {:workDoneProgress true
            :showMessage {:messageActionItem {:additionalPropertiesSupport true}}
            :showDocument {:support true}}})

(defn server-initialize! [server opts]
  (let [{:keys [root-path]} opts
        root-uri (path->uri root-path)
        params {:capabilities
                default-client-capabilities
                #_(:capabilities nvim-init-params)

                :processId (.pid (ProcessHandle/current))
                :rootPath root-path
                :rootUri root-uri
                :workDoneToken "initialize-1"
                :workspaceFolders [{:name root-path
                                    :uri root-uri}]}]
                ; :clientInfo {:name "lsp-sniffer" :version "0.1.0"}
                ; :initializationOptions {}
                ; :trace "off"
    ;; We don't need to queue requests because sending initialize with server-request! will block the thread until server responses with initialize.
    (let [response (server-request! server "initialize" params)]
      (reset! (::!initialization server) response)
      (server-message! server {:method "initialized" :params {} :jsonrpc "2.0"})
      response)))

(defn server-stop! [server]
  (server-request! server "shutdown")
  (server-message! server {:method "exit" :jsonrpc "2.0"})
  (let [{::keys [^Process process]} server]
    ;; First wait if the server stops by itself after receiving exit message
    (.waitFor process 1000 TimeUnit/MILLISECONDS)
    ;; Otherwise terminate it gracefully with destroy
    (when (.isAlive process)
      (.destroy process))
    ;; If the server does not stop within a given time, then terminate it forcibly with destroyForcibly
    (.waitFor process 1000 TimeUnit/MILLISECONDS)
    (when (.isAlive process)
      (.destroyForcibly process))))

(defn server-wait-for-progress! [server pred]
  (let [{::keys [!progresses]} server
        p (promise)
        k (Object.)]
    (add-watch !progresses k
               (fn [_ _ _ new-state]
                 (when (pred new-state)
                   (deliver p true))))
    (try
      @p
      (finally
        (remove-watch !progresses k)))))

(defn location-less-or-equal? [a b]
  (or (< (:line a) (:line b))
      (and (= (:line a) (:line b))
           (<= (:character a) (:character b)))))

(defn range-contains? [outer inner]
  (and (location-less-or-equal? (:start outer) (:start inner))
       (location-less-or-equal? (:end inner) (:end outer))))

(defn symbol->id [{:keys [uri sym]}]
  (let [{:keys [start end]} (:selectionRange sym)]
    (str uri "#L" (:line start) "C" (:character start) "-L" (:line end) "C" (:character end))))

(defn- send-did-open! [server uri file-path language-id]
  (assert language-id "Must specify langauge-id when opening documents")
  (server-message! server {:method "textDocument/didOpen"
                           :params {:textDocument {:uri uri
                                                   :languageId language-id
                                                   :version 1
                                                   :text (slurp file-path)}}
                           :jsonrpc "2.0"}))

(defn extract-graph [{:keys [server root-path source-paths source-pattern open-documents? language-id]}]
  (let [uri-base (str "file://" root-path "/")
        file-paths (->> source-paths
                        (mapcat (fn [path]
                                  (fs/glob (fs/file root-path path) source-pattern))))
        file-uris (map path->uri file-paths)
        file-uris-set (set file-uris)
        _ (when open-documents?
            (doseq [[uri path] (map vector file-uris file-paths)]
              (send-did-open! server uri (str path) language-id)))
        symbols (->> file-uris
                     (mapcat (fn [uri]
                               (->> (server-request! server "textDocument/documentSymbol"
                                                     {:textDocument {:uri uri}
                                                      :position {:character 0, :line 0}})
                                    (map (fn [sym]
                                           {:uri uri
                                            :sym (assoc sym :kind-kw (symbol-kind-lookup (:kind sym)))})))))
                     (doall))
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
        g (-> (lg/digraph)
              (lg/add-nodes* file-uris)
              (internal/add-clustered-namespace-hierarchy-path-based uri-base))
        g (reduce (fn [g node]
                    (la/add-attr g node :category "Namespace"))
                  g
                  (lg/nodes g))
        g (reduce
           (fn [g {:keys [id label parent]}]
             (-> g
                 (lg/add-nodes id)
                 (la/add-attr id :label label)
                 (la/add-attr id :parent parent)))
           g
           nodes)
        g (-> g
              (lg/add-edges* links))]
    g))

(defn graph->dgml [g]
  (let [namespace-with-nested-namespace? (->> (lg/nodes g)
                                              (keep #(la/attr g % :parent))
                                              set)]
    (xml/element ::dgml/DirectedGraph
                 {:xmlns "http://schemas.microsoft.com/vs/2009/dgml"}
                 (xml/element ::dgml/Nodes {}
                              (for [node-id (lg/nodes g)]
                                (xml/element ::dgml/Node
                                             (cond-> {:Id node-id
                                                      :Label (la/attr g node-id :label)}

                                               (= (la/attr g node-id :category) "Namespace")
                                               (assoc
                                                :Category "Namespace"
                                                :Group (if (namespace-with-nested-namespace? node-id) "Expanded" "Collapsed"))))))
                 (xml/element ::dgml/Links {}
                              (concat
                               (->> (lg/nodes g)
                                    (keep (fn [node-id]
                                            (when-some [parent (la/attr g node-id :parent)]
                                              (xml/element ::dgml/Link {:Source parent :Target node-id :Category "Contains"})))))
                               (for [[source target] (lg/edges g)]
                                 (xml/element ::dgml/Link {:Source source :Target target}))))
                 (xml/element ::dgml/Styles {} style/styles))))

(defn ->dgml [{:keys [root-path source-paths server-args initialize! source-pattern] :or {initialize! server-initialize!}}]
  (let [server (start-server {:args server-args})
        g (try
            (initialize! server {:root-path root-path})
            (extract-graph {:server server
                            :root-path root-path
                            :source-paths source-paths
                            :source-pattern source-pattern})
            (finally
              (server-stop! server)))]
    (graph->dgml g)))

(defn- normalize-opts [opts]
  (update opts :root-path #(.getCanonicalPath (io/file %))))

(defn extract-clojure [opts]
  (let [opts (normalize-opts opts)
        {:keys [root-path]} opts
        server (start-server {:args ["clojure-lsp"]})]
    (try
      (server-initialize! server {:root-path root-path})
      (extract-graph (merge {:source-paths ["src"]
                             :source-pattern "**.clj{,c,s}"
                             :server server}
                            opts))
      (finally
        (server-stop! server)))))

(defn extract-go [opts]
  (let [opts (normalize-opts opts)
        {:keys [root-path]} opts
        server (start-server {:args ["gopls"]})]
    (try
      (server-initialize! server {:root-path root-path})
      (extract-graph (merge {:source-paths ["."]
                             :source-pattern "**.go"
                             :server server}
                            opts))
      (finally
        (server-stop! server)))))

(defn extract-c [opts]
  (let [opts (normalize-opts opts)
        {:keys [root-path]} opts
        server (start-server {:args ["clangd"]})]
    (try
      (server-initialize! server {:root-path root-path})
      (extract-graph (merge {:source-paths ["."]
                             :source-pattern "**.{c,h}"
                             :open-documents? true
                             :language-id "c"
                             :server server}
                            opts))
      (finally
        (server-stop! server)))))

(defn extract-lua [opts]
  (let [opts (normalize-opts opts)
        {:keys [root-path]} opts
        server (start-server {:args ["lua-language-server"]})]
    (try
      (server-initialize! server {:root-path root-path})
      (extract-graph (merge {:source-paths ["lua"]
                             :source-pattern "**.lua"
                             :server server}
                            opts))
      (finally
        (server-stop! server)))))

(defn initialize-rust-analyzer! [server opts]
  (server-initialize! server opts)
  ;; It seems rust-analyzer needs to prime cache twice to return correct results.
  (server-wait-for-progress! server #(contains? % "rustAnalyzer/cachePriming"))
  (server-wait-for-progress! server #(empty? %))
  (server-wait-for-progress! server #(contains? % "rustAnalyzer/cachePriming"))
  (server-wait-for-progress! server #(empty? %))
  (println "custom initialize end"))

(defn extract-rust [opts]
  (let [opts (normalize-opts opts)
        {:keys [root-path]} opts
        ;; Try to prime caches first since waiting for priming mesages seems unreliable
        _ (shell "rust-analyzer prime-caches" root-path)
        server (start-server {:args ["rust-analyzer"]})]
    (try
      (initialize-rust-analyzer! server {:root-path root-path})
      (extract-graph (merge {:source-paths ["src"]
                             :source-pattern "**.rs"
                             :server server}
                            opts))
      (finally
        (server-stop! server)))))

(defn extract-zig [opts]
  (let [opts (normalize-opts opts)
        {:keys [root-path]} opts
        server (start-server {:args ["zls"]})]
    (try
      (server-initialize! server {:root-path root-path})
      (extract-graph (merge {:source-paths ["src"]
                             :source-pattern "**.zig"
                             :open-documents? true
                             :language-id "zig"
                             :server server}
                            opts))
      (finally
        (server-stop! server)))))

(defn extract-typescript [opts]
  (let [opts (normalize-opts opts)
        {:keys [root-path]} opts
        server (start-server {:args ["typescript-language-server" "--stdio"]})]
    (try
      (server-initialize! server {:root-path root-path})
      (extract-graph (merge {:source-paths ["src"]
                             :source-pattern "**.{j,t}s{,x}"
                             :open-documents? true
                             :language-id "typescript"
                             :server server}
                            opts))
      (finally
        (server-stop! server)))))

(defn decode-semantic-tokens [payload token-types token-modifiers]
  (let [{:keys [data]} payload]
    (loop [[delta-line delta-start-char length token-type-id token-modifiers-bits & remaining] data
           prev-line 0
           prev-start-char 0
           tokens []]
      (if-not delta-line
        tokens
        (let [line (+ prev-line delta-line)
              start-char (if (zero? delta-line)
                           (+ prev-start-char delta-start-char)
                           delta-start-char)
              token-type (get token-types token-type-id "unknown")
              modifiers (into #{}
                              (keep-indexed
                               (fn [idx _]
                                 (when (bit-test token-modifiers-bits idx)
                                   (get token-modifiers idx))))
                              token-modifiers)]
          (recur remaining line start-char
                 (conj tokens
                       {:line line
                        :startChar start-char
                        :length length
                        :tokenType token-type
                        :tokenModifiers modifiers})))))))

::process

::process

:stratify.lsp/process

(comment
  (let [data (->dgml {:root-path (.getCanonicalPath (io/file "../../test/resources/nested"))
                      :server-args ["clojure-lsp"]
                      :source-paths ["src"]
                      :source-pattern "**.clj{,c,s}"})]
    (sdgml/write-to-file "../../../../shared/lsp-nested-after.dgml" data))

  (let [data (->dgml {:root-path (.getCanonicalPath (io/file "../.."))
                      :server-args ["clojure-lsp"]
                      :source-paths ["src"]
                      :source-pattern "**.clj{,c,s}"})]
    (sdgml/write-to-file "../../../../shared/lsp-stratify.dgml" data))

  (def analysis (kondo/analysis ["../../test/resources/nested"]))

  (kondo/->graph analysis)
  (internal/analysis->graph {:analysis analysis})

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

(comment
  (def root-path (.getCanonicalPath (io/file "../scip/test/resources/sample-rs")))
  (do
    (def uri-base (str "file://" root-path "/"))
    (def server (start-server {:args ["rust-analyzer"]}))
    (initialize-rust-analyzer! server {:root-path root-path}))

  (let [uri (str uri-base "src/main.rs")]
    (->> (server-request! server "textDocument/documentSymbol"
                          {:position {:character 0, :line 0}, :textDocument {:uri uri}})))

  (let [data (->dgml {:root-path (.getCanonicalPath (io/file "../scip/test/resources/sample-rs"))
                      :server-args ["rust-analyzer"]
                      :source-paths ["src"]
                      :source-pattern "**.rs"
                      :initialize! initialize-rust-analyzer!})]
    (sdgml/write-to-file "../../../../shared/lsp-sample-rs.dgml" data))

  (let [data (graph->dgml (extract-rust {:root-path (.getCanonicalPath (io/file "../scip/test/resources/sample-rs"))}))]
    (sdgml/write-to-file "../../../../shared/lsp-sample-rs.dgml" data)))

(comment
  (def root-path (.getCanonicalPath (io/file "../scip/test/resources/sample-go")))
  (def uri-base (str "file://" root-path "/"))

  (def server (start-server {:args ["gopls"]}))

  (server-initialize! server {:root-path root-path})

  (extract-graph {:server server
                  :root-path root-path
                  :source-paths ["."]
                  :source-pattern "**.go"})

  (let [uri (str uri-base "main.go")]
    (->> (server-request! server "textDocument/documentSymbol"
                          {:position {:character 0, :line 0}, :textDocument {:uri uri}})))

  (server-stop! server)

  (let [data (->dgml {:root-path (.getCanonicalPath (io/file "../scip/test/resources/sample-go"))
                      :server-args ["gopls"]
                      :source-paths ["."]
                      :source-pattern "**.go"})]
    (sdgml/write-to-file "../../../../shared/lsp-sample-go2.dgml" data)))

(comment
  (def root-path (.getCanonicalPath (io/file "../scip/test/resources/sample-lua")))
  (def root-path (.getCanonicalPath (io/file "/home/me/code/lazy-lsp.nvim")))
  (def uri-base (str "file://" root-path "/"))

  (def server (start-server {:args ["lua-language-server"]}))

  (server-initialize! server {:root-path root-path})

  (def symbls (let [uri (str uri-base "lua/lazy-lsp/init.lua")]
                (->> (server-request! server "textDocument/documentSymbol"
                                      {:position {:character 0, :line 0}, :textDocument {:uri uri}})
                     (map (fn [sym]
                            (assoc sym :kind-kw (symbol-kind-lookup (:kind sym))))))))
  (tap> symbls)

  (def symbls (let [uri (str uri-base "lua/lazy-lsp/helpers.lua")]
                (->> (server-request! server "textDocument/documentSymbol"
                                      {:position {:character 0, :line 0}, :textDocument {:uri uri}})
                     (map (fn [sym]
                            (assoc sym :kind-kw (symbol-kind-lookup (:kind sym))))))))
  (tap> symbls)

  (let [uri (str uri-base #_"lua/lazy-lsp/init.lua"
                 "lua/main.lua")
        response (server-request! server "textDocument/semanticTokens/full"
                                  {:textDocument {:uri uri}})
        {:keys [tokenTypes tokenModifiers]} (->> @(::!initialization server)
                                                 :capabilities :semanticTokensProvider :legend)]
    (decode-semantic-tokens response tokenTypes tokenModifiers))

  (extract-graph {:server server
                  :root-path root-path
                  :source-paths ["lua"]
                  :source-pattern "**.lua"})

  (server-stop! server)

  (let [data (->dgml {:root-path (.getCanonicalPath (io/file "../scip/test/resources/sample-lua"))
                      :server-args ["lua-language-server"]
                      :source-paths ["."]
                      :source-pattern "**.lua"})]
    (sdgml/write-to-file "../../../../shared/lsp-sample-lua.dgml" data))

  (let [data (graph->dgml (extract-lua {:root-path (.getCanonicalPath (io/file "/home/me/code/lazy-lsp.nvim"))}))]
    (sdgml/write-to-file "../../../../shared/lsp-lua-lazy-lsp.dgml" data)))

(comment
  (def root-path (.getCanonicalPath (io/file "../scip/test/resources/sample-ts")))
  (def uri-base (str "file://" root-path "/"))

  (def server (start-server {:args ["typescript-language-server" "--stdio"]}))

  (server-initialize! server {:root-path root-path})

  (extract-graph {:server server
                  :root-path root-path
                  :source-paths ["."]
                  :source-pattern "**.{j,t}s{,x}"})

  (let [uri (str uri-base "src/main.ts")]
    (->> (server-request! server "textDocument/documentSymbol"
                          {:position {:character 0, :line 0}, :textDocument {:uri uri}})))

  (server-stop! server)

  (let [data (->dgml {:root-path root-path
                      :server-args ["typescript-language-server" "--stdio"]
                      :source-paths ["src"]
                      :source-pattern "**.{j,t}s{,x}"})]
    (sdgml/write-to-file "../../../../shared/lsp-sample-ts.dgml" data)))
