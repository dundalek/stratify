(ns io.github.dundalek.stratify.scip-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.stratify.scip :as scip]))

(deftest scip-go-graph
  (let [index (scip/read-scip-index "test/resources/scip/go.scip")]
    (is (= {:adj {"main.go" #{"greet/greet.go"}}}
           (scip/->graph index)))))

(deftest scip-rust-graph
  (let [index (scip/read-scip-index "test/resources/scip/rust.scip")]
    (is (= {:adj {"src/main.rs" #{"src/greeting.rs"}}}
           (scip/->graph index)))))

(deftest scip-typescript-graph
  (let [index (scip/read-scip-index "test/resources/scip/typescript.scip")]
    (is (= {:adj {"src/main.ts" #{"src/greeting.ts" "src/main.ts"}}}
           (scip/->graph index)))))

(deftest scip-python-graph
  (let [index (scip/read-scip-index "test/resources/scip/python.scip")]
    (is (= {:adj {"main.py" #{"greetings.py"}}}
           (scip/->graph index)))))
