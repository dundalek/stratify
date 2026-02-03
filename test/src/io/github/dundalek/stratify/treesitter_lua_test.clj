(ns io.github.dundalek.stratify.treesitter-lua-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.test-utils :refer [extract-relative-graph make-digraph]]
   [io.github.dundalek.stratify.treesitter-lua :as treesitter-lua]))

(deftest extract-lua-greeting
  (is (= (make-digraph
          {:adj {"lua/main.lua" #{"lua/greeting.lua"}}
           :attrs {"lua" {:category "Namespace", :label "lua", :parent nil}
                   "lua/greeting.lua" {:category "Namespace", :label "greeting.lua", :parent "lua"}
                   "lua/main.lua" {:category "Namespace", :label "main.lua", :parent "lua"}}})
         (extract-relative-graph treesitter-lua/extract-lua "test/resources/code/lua/greeting"))))

(deftest extract-lua-nested
  (is (= (make-digraph
          {:adj {"lua/example/foo.lua" #{"lua/example/foo/bar.lua"}}
           :attrs {"lua" {:category "Namespace", :label "lua", :parent nil}
                   "lua/example" {:category "Namespace", :label "example", :parent "lua"}
                   "lua/example/foo" {:category "Namespace", :label "foo", :parent "lua/example"}
                   "lua/example/foo.lua" {:category "Namespace", :label "foo.lua", :parent "lua/example"}
                   "lua/example/foo/bar.lua" {:category "Namespace", :label "bar.lua", :parent "lua/example/foo"}}})
         (extract-relative-graph treesitter-lua/extract-lua "test/resources/code/lua/nested"))))
