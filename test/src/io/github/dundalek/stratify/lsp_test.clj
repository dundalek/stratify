(ns io.github.dundalek.stratify.lsp-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.lsp :as lsp]
   [io.github.dundalek.stratify.test-utils :refer [extract-relative-graph make-digraph]]))

(deftest extract-clojure
  (is (= (make-digraph
          {:adj {"src/example/foo.clj#L0C4-L0C15" #{"src/example/foo/bar.clj#L0C4-L0C19"
                                                    "src/example/foo/bar.clj#L2C6-L2C7"},
                 "src/example/foo.clj#L3C6-L3C7" #{"src/example/foo/bar.clj#L2C6-L2C7"}}
           :attrs {"src" {:category "Namespace", :label "src", :parent nil},
                   "src/example" {:category "Namespace", :label "example", :parent "src"},
                   "src/example/foo" {:category "Namespace", :label "foo", :parent "src/example"},
                   "src/example/foo.clj" {:category "Namespace", :label "foo.clj", :parent "src/example"},
                   "src/example/foo.clj#L0C4-L0C15" {:label "example.foo", :parent "src/example/foo.clj"},
                   "src/example/foo.clj#L3C6-L3C7" {:label "x", :parent "src/example/foo.clj"},
                   "src/example/foo/bar.clj" {:category "Namespace",
                                              :label "bar.clj",
                                              :parent "src/example/foo"},
                   "src/example/foo/bar.clj#L0C4-L0C19" {:label "example.foo.bar",
                                                         :parent "src/example/foo/bar.clj"},
                   "src/example/foo/bar.clj#L2C6-L2C7" {:label "y", :parent "src/example/foo/bar.clj"}}})
         (extract-relative-graph lsp/extract-clojure "test/resources/code/clojure/nested"))))

(deftest extract-go
  (is (= (make-digraph
          {:adj {"main.go#L7C5-L7C9" #{"greet/greet.go#L2C5-L2C13"}},
           :attrs {"greet" {:category "Namespace", :label "greet", :parent nil},
                   "greet/greet.go" {:category "Namespace", :label "greet.go", :parent "greet"},
                   "greet/greet.go#L2C5-L2C13" {:label "TheWorld", :parent "greet/greet.go"},
                   "main.go" {:category "Namespace", :label "main.go", :parent nil},
                   "main.go#L7C5-L7C9" {:label "main", :parent "main.go"}}})
         (extract-relative-graph lsp/extract-go "test/resources/code/go/greeting"))))

(deftest extract-c
  (is (= (make-digraph
          {:adj {"main.c#L3C4-L3C8" #{"greeting.c#L2C12-L2C17"}},
           :attrs {"greeting.c" {:category "Namespace", :label "greeting.c", :parent nil},
                   "greeting.c#L2C12-L2C17" {:label "greet", :parent "greeting.c"},
                   "greeting.h" {:category "Namespace", :label "greeting.h", :parent nil},
                   "greeting.h#L3C12-L3C17" {:label "greet", :parent "greeting.h"},
                   "main.c" {:category "Namespace", :label "main.c", :parent nil},
                   "main.c#L3C4-L3C8" {:label "main", :parent "main.c"}}})
         (extract-relative-graph lsp/extract-c "test/resources/code/c/greeting"))))

(deftest extract-lua
  (is (= (make-digraph
          {:adj {"lua/greeting.lua" #{"lua/greeting.lua#L0C6-L0C7"},
                 "lua/greeting.lua#L2C11-L2C16" #{"lua/greeting.lua#L0C6-L0C7"},
                 "lua/main.lua" #{"lua/main.lua#L2C15-L2C19"},
                 "lua/main.lua#L2C15-L2C19" #{"lua/greeting.lua#L2C11-L2C16" "lua/main.lua#L0C6-L0C14"}},
           :attrs {"lua" {:category "Namespace", :label "lua", :parent nil},
                   "lua/greeting.lua" {:category "Namespace", :label "greeting.lua", :parent "lua"},
                   "lua/greeting.lua#L0C6-L0C7" {:label "M", :parent "lua/greeting.lua"},
                   "lua/greeting.lua#L2C11-L2C16" {:label "M.greet", :parent "lua/greeting.lua"},
                   "lua/main.lua" {:category "Namespace", :label "main.lua", :parent "lua"},
                   "lua/main.lua#L0C6-L0C14" {:label "greeting", :parent "lua/main.lua"},
                   "lua/main.lua#L2C15-L2C19" {:label "main", :parent "lua/main.lua"}}})
         (extract-relative-graph lsp/extract-lua "test/resources/code/lua/greeting"))))

;; rust analyzer is a bit flaky
#_(deftest extract-rust
    (is (= (make-digraph
            {:adj {"src/main.rs#L2C3-L2C7" #{"src/greeting.rs#L0C7-L0C12" "src/main.rs#L0C4-L0C12"}},
             :attrs {"src" {:category "Namespace", :label "src", :parent nil},
                     "src/greeting.rs" {:category "Namespace", :label "greeting.rs", :parent "src"},
                     "src/greeting.rs#L0C7-L0C12" {:label "greet", :parent "src/greeting.rs"},
                     "src/main.rs" {:category "Namespace", :label "main.rs", :parent "src"},
                     "src/main.rs#L0C4-L0C12" {:label "greeting", :parent "src/main.rs"},
                     "src/main.rs#L2C3-L2C7" {:label "main", :parent "src/main.rs"}}})
           (extract-relative-graph lsp/extract-rust "test/resources/code/rust/greeting"))))

(deftest extract-zig
  (is (= (make-digraph
          {:adj {"src/main.zig#L3C7-L3C11" #{"src/main.zig#L0C6-L0C9" "src/main.zig#L1C6-L1C14"}}
           :attrs {"src" {:category "Namespace", :label "src", :parent nil},
                   "src/greeting.zig" {:category "Namespace", :label "greeting.zig", :parent "src"},
                   "src/greeting.zig#L0C7-L0C12" {:label "greet", :parent "src/greeting.zig"},
                   "src/main.zig" {:category "Namespace", :label "main.zig", :parent "src"},
                   "src/main.zig#L0C6-L0C9" {:label "std", :parent "src/main.zig"},
                   "src/main.zig#L1C6-L1C14" {:label "greeting", :parent "src/main.zig"},
                   "src/main.zig#L3C7-L3C11" {:label "main", :parent "src/main.zig"}}})
         (extract-relative-graph lsp/extract-zig "test/resources/code/zig/greeting"))))

(deftest extract-typescript
  (is (= (make-digraph
          {:adj {"src/main.ts#L2C9-L2C13" #{"src/greeting.ts#L0C16-L0C21"}
                 "src/main.ts" #{"src/greeting.ts#L0C16-L0C21" "src/main.ts#L2C9-L2C13"}}
           :attrs {"src" {:category "Namespace", :label "src", :parent nil},
                   "src/greeting.ts" {:category "Namespace", :label "greeting.ts", :parent "src"},
                   "src/greeting.ts#L0C16-L0C21" {:label "greet", :parent "src/greeting.ts"},
                   "src/main.ts" {:category "Namespace", :label "main.ts", :parent "src"},
                   "src/main.ts#L2C9-L2C13" {:label "main", :parent "src/main.ts"}}})
         (extract-relative-graph lsp/extract-typescript "test/resources/code/typescript/greeting"))))

(deftest location-less-or-equal?
  (is (true? (lsp/location-less-or-equal? {:line 0 :character 0} {:line 0 :character 15})))
  (is (true? (lsp/location-less-or-equal? {:line 0 :character 15} {:line 1 :character 1})))
  (is (false? (lsp/location-less-or-equal? {:line 1 :character 15} {:line 1 :character 1})))
  (is (false? (lsp/location-less-or-equal? {:line 1 :character 15} {:line 0 :character 1}))))

(deftest decode-semantic-tokens
  (is (= [{:line 2, :startChar 5, :length 3, :tokenType "property", :tokenModifiers #{"private" "static"}}
          {:line 2, :startChar 10, :length 4, :tokenType "type", :tokenModifiers #{}}
          {:line 5, :startChar 2, :length 7, :tokenType "class", :tokenModifiers #{}}]
         (lsp/decode-semantic-tokens
          {:data [2,5,3,0,3,  0,5,4,1,0,  3,2,7,2,0]}
          ["property", "type", "class"]
          ["private", "static"]))))
