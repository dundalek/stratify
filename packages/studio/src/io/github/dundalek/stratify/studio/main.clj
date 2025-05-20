(ns io.github.dundalek.stratify.studio.main
  (:require
   [clojure.java.io :as io]
   [portal.api :as p]
   [portal.viewer :as pv]
   [portal.runtime.jvm.server :as portal-server]))

(comment
  (tap>
   (pv/default
    {"a" #{"b" "c"}}
    :io.github.dundalek.stratify.studio.viewers/viz))

  (tap>
   (pv/default
    {"a" #{"b" "c"}
     "b" #{"d" "e"}}
    :io.github.dundalek.stratify.studio.viewers/viz)))

(comment
  (defmethod portal-server/route [:get "/main.js"] [request]
    {:status  200
     :headers {"Content-Type" "text/javascript"}
     :body (slurp (case (-> request :session :options :mode)
                    :dev "packages/portal/resources/portal-dev/main.js"
                    "packages/portal/resources/portal/main.js"))})

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
  (p/eval-str (slurp (io/resource "io/github/dundalek/stratify/studio/viewers.cljs"))))
