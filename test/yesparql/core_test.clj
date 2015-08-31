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

(defquery expression-atlas
  "yesparql/samples/expression-atlas.sparql"
  {:connection "https://www.ebi.ac.uk/rdf/services/atlas/sparql"
   :timeout 1500})

(defquery dbpedia-2
  "yesparql/samples/dbpedia-2.sparql"
  {:connection "http://dbpedia.org/sparql"
   :timeout 1500})

(expect
 [{"country_name" {:type "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString", :value "Ethiopia", :lang :en},
   "population" {:type "http://www.w3.org/2001/XMLSchema#integer", :value 87952991}}
  {"country_name" {:type "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString", :value "Afghanistan", :lang :en},
   "population" {:type "http://www.w3.org/2001/XMLSchema#integer", :value 31822848}}
  {"country_name" {:type "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString", :value "Uzbekistan", :lang :en},
   "population" {:type "http://www.w3.org/2001/XMLSchema#integer", :value 30185000}}
  {"country_name" {:type "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString", :value "Burkina Faso", :lang :en},
   "population" {:type "http://www.w3.org/2001/XMLSchema#integer", :value 17322796}}
  {"country_name" {:type "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString", :value "Malawi", :lang :en},
   "population" {:type "http://www.w3.org/2001/XMLSchema#integer", :value 16407000}}]
 (with-open [r (dbpedia-2)] (into [] r)))

;; Test limit
(expect 4 (count (into [] (select-all))))
(expect 3 (count (into [] (select-all {:limit 3}))))

;; Test offset
(expect
 [{"object" {:type "http://www.w3.org/2001/XMLSchema#string", :value "A second book"},
   "subject" "http://example/book2",
   "predicate" "http://purl.org/dc/elements/1.1/title"}
  {"object" {:type "http://www.w3.org/2001/XMLSchema#string", :value "A third book"},
   "subject" "http://example/book3",
   "predicate" "http://purl.org/dc/elements/1.1/title"}]
 (into [] (select-all {:limit 2 :offset 2})))

(expect
 [{"object" {:type "http://www.w3.org/2001/XMLSchema#string", :value "A new book"},
   "subject" "http://example/book1",
   "predicate" "http://purl.org/dc/elements/1.1/title"}
  {"object" {:type "http://www.w3.org/2001/XMLSchema#string", :value "A second book"},
   "subject" "http://example/book2",
   "predicate" "http://purl.org/dc/elements/1.1/title"}]
 (into [] (select-all {:limit 2 :offset 1})))
