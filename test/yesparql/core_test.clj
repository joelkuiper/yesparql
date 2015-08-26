(ns yesparql.core-test
  (:import [java.net URI URL URLEncoder])
  (:require [clojure.string :refer [upper-case]]
            [expectations :refer :all]

            [yesparql.tdb :as tdb]
            [yesparql.sparql :refer :all]
            [yesparql.core :refer :all]))

;; Test in-memory SPARQL

(defn triple-count
  [results]
  (count (get-in (result->clj (->result results)) [:results :bindings])))

(def tdb (tdb/create-in-memory))


(defquery select-all
  "yesparql/samples/select.sparql"
  {:connection tdb})

(defquery update-books!
  "yesparql/samples/update.sparql"
  {:connection tdb})

(defquery ask-book
  "yesparql/samples/ask.sparql"
  {:connection tdb})

(defquery construct-books
  "yesparql/samples/construct.sparql"
  {:connection tdb})

(expect 0 (triple-count (select-all)))

;; With 4 books
(expect 4 (do
            (update-books!)
            (triple-count (select-all))))

(expect true (ask-book))

(expect true (not (nil? (model->json-ld (->model (construct-books))))))

(expect true (not (nil? (model->rdf+xml (result->model (->result (select-all)))))))

(defquery select-book
  "yesparql/samples/select-bindings.sparql"
  {:connection tdb})

(expect {:type "literal", :value "A default book"}
        (:title (first (get-in
                        (result->clj (->result (select-book {:bindings {"book" (URI. "http://example/book0")}})))
                        [:results :bindings]))))

;; Test with function override
(expect "SELECT ?subject ?predicate ?object\nWHERE {\n  ?subject ?predicate ?object\n}\nLIMIT 25"
        (select-all {:query-fn (fn [data-set query call-options & args]  (str query))}))

;; Test with comments
(defquery select-foo
  "yesparql/samples/with-comments.sparql"
  {:connection tdb})

(expect true (not (nil? (->result (select-foo)))))

;; Test remote SPARQL endpoints
(defquery dbpedia-select
  "yesparql/samples/remote-query.sparql"
  {:connection "http://dbpedia.org/sparql"})

(expect 10
        (triple-count
         (dbpedia-select {:timeout 500
                          :bindings {"subject" (URI. "http://dbpedia.org/resource/Category:1952_deaths")}})))

(defquery drugbank
  "yesparql/samples/drugbank.sparql"
  {:connection "http://drugbank.bio2rdf.org/sparql"})
