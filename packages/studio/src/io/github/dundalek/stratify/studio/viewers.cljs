(ns io.github.dundalek.stratify.studio.viewers
  (:require
   ["react" :as react]
   [io.github.dundalek.stratify.studio.cytoscape-elk :as cytoscape-elk]
   [io.github.dundalek.stratify.studio.d3g :as d3g]
   [loom.graph :as lg]
   [portal.ui.api :as pua]))

(defn detect-container-dimensions []
  {:width (- js/window.innerWidth 80)
   :height (- js/window.innerHeight 200)})

(defn graph-container [{:keys [element make-layout make-renderer full-graph graph
                               on-node-click on-cluster-click]}]
  (let [element-ref (react/useRef)
        [render set-render!] (react/useState nil)
        [laidout-graph set-laidout-graph!] (react/useState nil)
        [dimensions set-dimensions!] (react/useState (detect-container-dimensions))
        handle-resize (react/useCallback
                       #(set-dimensions! (detect-container-dimensions)))
        {:keys [width height]} dimensions]

    (react/useEffect
     (fn []
       (.addEventListener js/window "resize" handle-resize)
       #(.removeEventListener js/window "resize" handle-resize))
     #js [])

    (react/useEffect
     (fn []
       (set-render!
        (fn []
          (let [container (.-current element-ref)
                make-renderer make-renderer]
            (set! (.-innerHTML container) "")
            (make-renderer {:container container
                            :on-node-click on-node-click
                            :on-cluster-click on-cluster-click}))))
       js/undefined)
     #js [(.-current element-ref) make-renderer on-node-click on-cluster-click])

    (react/useEffect
     (fn []
       (if graph
         (make-layout {:graph graph
                       :on-update set-laidout-graph!})
         js/undefined))
     #js [make-layout graph set-laidout-graph!])

    (react/useEffect
     (fn []
       (when (and render laidout-graph full-graph)
         (render {:graph laidout-graph
                  :full-graph full-graph
                  :show-clusters? true}))
       js/undefined)
     #js [render laidout-graph full-graph])

    [element {:ref element-ref
              :style {:width width :height height} #_{:flexGrow 1}}]))

(defn make-layout [{:keys [graph on-update]}]
  (cytoscape-elk/layout
   {:graph graph
    :on-update on-update
    :layout cytoscape-elk/layout-elk}))

(def make-renderer d3g/make-renderer)

(defn inspect-hello [value]
  (let [graph (lg/digraph value)]
    [graph-container
     {:element :svg
      :make-layout make-layout
      :make-renderer make-renderer
      :full-graph graph
      :graph graph
      :on-node-click #()
      :on-cluster-click #()}]))

(defn component-hello [_]
  [:div "Hello World!"])

(def viewer
  {:name ::viz
   :predicate (constantly true)
   :component inspect-hello})

(def viewer-hello
  {:name ::hello
   :predicate (constantly true)
   :component component-hello})

(pua/register-viewer! viewer)
(pua/register-viewer! viewer-hello)
