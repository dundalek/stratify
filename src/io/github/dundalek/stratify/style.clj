(ns io.github.dundalek.stratify.style
  (:require
   [clojure.data.xml :as xml]
   [xmlns.http%3A%2F%2Fschemas.microsoft.com%2Fvs%2F2009%2Fdgml :as-alias dgml]))

(def theme
  {::node-text-color "#FFFFFF"

   ::namespace-color "#295B71"
   ::namespace-stroke-color "#34749A"

   ::function-color "#A75529"
   ::function-stroke-color "#C4632F"

   ::var-color "#3A6C5F"
   ::var-stroke-color "#2A8469"

   ::macro-color "#714164"
   ::macro-stroke-color "#874381"})

(defn color-add-alpha [color alpha]
  (assert (and (string? color)
               (= (first color) \#)
               (= (count color) 7)))
  (assert (and (string? alpha)
               (= (count alpha) 2)))
  (str "#" alpha (subs color 1)))

(defn property-setter-elements [properties]
  (for [[k v] properties]
    (xml/element ::dgml/Setter {:Property (name k) :Value v})))

(def styles
  [(xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Namespace" :ValueLabel "True"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Namespace')"})
                (property-setter-elements  {:Background (::namespace-color theme)
                                            :Stroke (::namespace-stroke-color theme)
                                            :Foreground (::node-text-color theme)}))
   (xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Function" :ValueLabel "Public"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Function') and Access = 'Public'"})
                (property-setter-elements {:Background (::function-color theme)
                                           :Stroke (::function-stroke-color theme)
                                           :Foreground (::node-text-color theme)}))
   (xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Function" :ValueLabel "Private"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Function') and  Access = 'Private'"})
                (property-setter-elements {:Background (color-add-alpha (::function-color theme) "66")
                                           :Stroke (::function-stroke-color theme)
                                           :StrokeDashArray "3,6"
                                           :Foreground (::node-text-color theme)}))
   (xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Macro" :ValueLabel "Public"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Macro') and Access = 'Public'"})
                (property-setter-elements {:Background (::macro-color theme)
                                           :Stroke (::macro-stroke-color theme)
                                           :Foreground (::node-text-color theme)}))
   (xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Macro" :ValueLabel "Private"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Macro') and Access = 'Private'"})
                (property-setter-elements {:Background (color-add-alpha (::macro-color theme) "66")
                                           :Stroke (::macro-stroke-color theme)
                                           :StrokeDashArray "3,6"
                                           :Foreground (::node-text-color theme)}))
   (xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Var" :ValueLabel "Public"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Var') and Access = 'Public'"})
                (property-setter-elements {:Background (::var-color theme)
                                           :Stroke (::var-stroke-color theme)
                                           :Foreground (::node-text-color theme)}))
   (xml/element ::dgml/Style
                {:TargetType "Node" :GroupLabel "Var" :ValueLabel "Private"}
                (xml/element ::dgml/Condition {:Expression "HasCategory('Var') and Access = 'Private'"})
                (property-setter-elements {:Background (color-add-alpha (::var-color theme) "66")
                                           :Stroke (::var-stroke-color theme)
                                           :StrokeDashArray "3,6"
                                           :Foreground (::node-text-color theme)}))
   (xml/element ::dgml/Style
                {:TargetType "Link" :GroupLabel "Link" :ValueLabel "Private Reference"}
                (xml/element ::dgml/Condition {:Expression "Target.Access = 'Private'"})
                (property-setter-elements {:StrokeDashArray "4,2"}))])
