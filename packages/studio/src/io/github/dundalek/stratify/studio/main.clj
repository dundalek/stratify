(ns io.github.dundalek.stratify.studio.main
  (:require
   [clojure.java.io :as io]
   [portal.api :as p]
   [portal.viewer :as pv]
   [portal.runtime.jvm.server :as portal-server]))

(defmethod portal-server/route [:get "/main.js"] [request]
  {:status  200
   :headers {"Content-Type" "text/javascript"}
   :body (slurp
          (io/resource "portal-dev/main.js")
          #_(case (-> request :session :options :mode)
              :dev "portal-dev/main.js"
              "portal/main.js"))})

(defn open [g]
  (p/open)
  (p/submit
   (pv/default g :io.github.dundalek.stratify.studio.viewers/viz)))

(comment
  (tap>
   (pv/default
    {:adj {"a" #{"b" "c"}}}
    :io.github.dundalek.stratify.studio.viewers/viz))

  (tap>
   (pv/default
    {:adj {"a" #{"b" "c"}
           "b" #{"d" "e"}}}
    :io.github.dundalek.stratify.studio.viewers/viz)))

(comment

  (require '[portal.runtime.jvm.server as portal-server])

  (do
    (p/open)
    (add-tap #'p/submit))

  (require '[portal.viewer :as pv])
  (tap>
   (pv/default {}
               :io.github.dundalek.stratify.studio.viewers/hello))

  (tap>
   (pv/default {}
               :io.github.dundalek.stratify.studio.viewers/hello2)))

(comment
  (p/eval-str (slurp (io/resource "io/github/dundalek/stratify/studio/viewers.cljs")))

  @#'portal.runtime/tap-list)
