(ns io.github.dundalek.stratify.style-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.style :as style]))

(deftest color-add-alpha
  (is (= "#EF123456" (style/color-add-alpha "#123456" "EF"))))
