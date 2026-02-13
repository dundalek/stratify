(ns io.github.dundalek.stratify.kondo
  (:require
   #?@(:bb [[babashka.pods :as pods]]
       :clj [[clj-kondo.core :as clj-kondo]])))

#?(:bb (pods/load-pod 'clj-kondo/clj-kondo "2024.05.24"))

#?(:bb (require '[pod.borkdude.clj-kondo :as clj-kondo]))

(defn- run-kondo [paths]
  (clj-kondo/run!
   {:lint paths
    :config {:output {:analysis {:keywords true}}}}))

(defn analysis [paths]
  (:analysis (run-kondo paths)))

(defn ->graph [analysis]
  (let [{:keys [namespace-usages namespace-definitions]} analysis
        own-namespace? (->> namespace-definitions
                            (map :name)
                            (into #{}))
        ;; Have an empty set for each defined namespace as a base,
        ;; so that unconnected namespaces are also included.
        adj (->> namespace-definitions
                 (reduce (fn [m {:keys [name]}]
                           (assoc m (str name) #{}))
                         {}))
        adj (->> namespace-usages
                 (filter (comp own-namespace? :to))
                 (reduce (fn [m {:keys [from to]}]
                           (update m (str from) (fnil conj #{}) (str to)))
                         adj))]
    adj))
