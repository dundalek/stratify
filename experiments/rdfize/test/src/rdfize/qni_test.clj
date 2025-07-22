(ns rdfize.qni-test
  (:require
   [clojure.test :refer [are deftest is]]
   [org.example :as example]
   [rdfize.qni :as qni]))

(def uri->namespaced-test-cases
  [["com.example.subdomain.$.foo.bar"
    "http://subdomain.example.com/foo/bar"]
   ["com%3A80.example.user%40subdomain.$.foo.bar%2Ebaz%3Fparam%3Dxyz.$$.fragment"
    "http://user@subdomain.example.com:80/foo/bar.baz?param=xyz#fragment"]
   ["com.example.subdomain"
    "http://subdomain.example.com"]
   ["com%3A443.example.subdomain"
    "http://subdomain.example.com:443"]
   ["com.example.subdomain.$.path"
    "http://subdomain.example.com/path"]
   ["com%3A8080.example.user%40subdomain.$.api.v1%3Ftoken%3Dabc.$$.section"
    "http://user@subdomain.example.com:8080/api/v1?token=abc#section"]
   ["org.example.$.deep.nested.path%2Ewith%2Edots"
    "http://example.org/deep/nested/path.with.dots"]
   ["localhost"
    "http://localhost"]
   ["localhost%3A3000.$.app"
    "http://localhost:3000/app"]
   ["1%3A9000.1.168.admin%40root%40192"
    "http://admin@root@192.168.1.1:9000"]
   ["com.example.test.$.path%3Fquery%3Dvalue%26other%3Dtest.$$.frag"
    "http://test.example.com/path?query=value&other=test#frag"]
   ["com.example.subdomain.$.foo.bar.$$.fragment"
    "http://subdomain.example.com/foo/bar#fragment"]
   ["com.example.subdomain.$.foo.bar.$$.fragment%2Fxyz"
    "http://subdomain.example.com/foo/bar#fragment/xyz"]])

(deftest uri->namespaced-test
  (doseq [[expected input] uri->namespaced-test-cases]
    (is (= expected (qni/uri->namespaced input)))))

(deftest namespaced->uri-test
  (doseq [[input expected] uri->namespaced-test-cases]
    (is (= expected (qni/namespaced->uri input)))))

(deftest namespace-triple-test
  (is (= ["http://example.org/john"
          "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
          "http://xmlns.com/foaf/0.1/Person"]
         (mapv qni/namespaced-kw->uri example/triple))))

(deftest encode-initial-digit-test
  (are [input expected] (= expected (#'qni/encode-initial-digit input))
    "123" "%3123"
    "0abc" "%30abc"
    "9xyz" "%39xyz"
    "5" "%35"
    "a12" "a12"
    "abc123" "abc123"
    "xyz" "xyz"))
