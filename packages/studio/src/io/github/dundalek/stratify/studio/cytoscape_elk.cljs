(ns io.github.dundalek.stratify.studio.cytoscape-elk
  (:require
   ["cytoscape" :as cytoscape]
   ["cytoscape-elk" :as cytoscape-elk]
   [io.github.dundalek.stratify.studio.graph :as graph]))

(.use cytoscape cytoscape-elk)

(defn make-animation-debouncer [f]
  (let [!request-id (atom js/undefined)]
    (fn [& args]
      (js/window.cancelAnimationFrame @!request-id)
      (reset! !request-id (js/window.requestAnimationFrame #(apply f args))))))

(defonce ^:private dummy-div
  (let [container (js/document.createElement "div")]
    (.appendChild js/document.body container)
    container))

(def layout-elk
  #js {:name "elk"
       :nodeDimensionsIncludeLabels false
       :elk #js {:algorithm "layered"
                 :elk.direction "DOWN"
                 :elk.hierarchyHandling "INCLUDE_CHILDREN"}})

(defn layout [{:keys [graph on-update layout]}]
  (let [elements (graph/loom->js graph)
        cy (cytoscape
            ;; For some reason when not having a container animated layout like
            ;; Cola hangs even when passing null renderer or headless options.
            ;; So we pass a dummy node and unmount right afterwards as a
            ;; workaround.
            #js {:container dummy-div
                 ; :renderer "null"
                 ; :headless true
                 :elements elements})
                 ; :style style-grey})
        _ (.unmount cy)

        !ready (atom false)
        layout (.makeLayout cy layout)
        on-update-debounced (make-animation-debouncer
                             (fn []
                               (when @!ready
                                 (let [laidout-graph (graph/cy->loom cy)]
                                       ; _ (assert (= (graph/null-layout graph)
                                       ;              (graph/null-layout laidout-graph))
                                       ;           "Layout must not return different collection of edges/nodes")]
                                   (on-update laidout-graph)))))]
    (doto cy
      (.on "position" (fn [_ev]
                        ; (js/console.log ">> position" ev)
                        (on-update-debounced))))
    (doto layout
      (.on "layoutstop"
           (fn [ev]
             ; (js/console.log ">>layout stop" ev)
             (reset! !ready true)
             (on-update-debounced)))
      (.on "layoutready"
           (fn [_ev]
             ; (js/console.log ">>layout ready" ev)
             (reset! !ready true)
             (on-update-debounced))))
      ; (.on "layoutstart"
      ;      (fn [ev]
      ;        (js/console.log ">>layout start" ev))))
    (.run layout)
    #(.stop layout)))
