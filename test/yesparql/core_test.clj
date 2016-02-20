(ns yesparql.core-test
  (:import [java.net URI URL URLEncoder])
  (:require [clojure.string :refer [upper-case]]
            [expectations :refer :all]
            [clojure.java.io :as io]
            [yesparql.tdb :as tdb]
            [yesparql.sparql :refer :all]
            [yesparql.core :refer :all]))

;; Test in-memory SPARQL

(defn triple-count
  [results]
  (count (into [] results)))

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

(expect
 [{:s "http://example/book0",
   :p "http://purl.org/dc/elements/1.1/title",
   :o {:type "http://www.w3.org/2001/XMLSchema#string", :value "A default book"}}
  {:s "http://example/book1",
   :p "http://purl.org/dc/elements/1.1/title",
   :o {:type "http://www.w3.org/2001/XMLSchema#string", :value "A new book"}}
  {:s "http://example/book2",
   :p "http://purl.org/dc/elements/1.1/title",
   :o {:type "http://www.w3.org/2001/XMLSchema#string", :value "A second book"}}
  {:s "http://example/book3",
   :p "http://purl.org/dc/elements/1.1/title",
   :o {:type "http://www.w3.org/2001/XMLSchema#string", :value "A third book"}}]
 (into [] (map #(into {} %) (construct-books)))) ; returns yesparql.sparql.Triple

;; Test conversion
(def model-ttl
  "@prefix rs:    <http://www.w3.org/2001/sw/DataAccess/tests/result-set#> .\n@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .\n\n[ a                  rs:ResultSet ;\n  rs:resultVariable  \"object\" , \"predicate\" , \"subject\" ;\n  rs:size            \"4\"^^xsd:int ;\n  rs:solution        [ rs:binding  [ rs:value     \"A third book\" ;\n                                     rs:variable  \"object\"\n                                   ] ;\n            rs:binding  [ rs:value     <http://purl.org/dc/elements/1.1/title> ;\n                          rs:variable  \"predicate\"\n                        ] ;\n            rs:binding  [ rs:value     <http://example/book3> ;\n                          rs:variable  \"subject\"\n                        ] ] ;\n  rs:solution        [ rs:binding  [ rs:value     \"A second book\" ;\n                                     rs:variable  \"object\"\n                                   ] ;\n            rs:binding  [ rs:value     <http://purl.org/dc/elements/1.1/title> ;\n                          rs:variable  \"predicate\"\n                        ] ;\n            rs:binding  [ rs:value     <http://example/book2> ;\n                          rs:variable  \"subject\"\n                        ] ] ;\n  rs:solution        [ rs:binding  [ rs:value     \"A new book\" ;\n                                     rs:variable  \"object\"\n                                   ] ;\n            rs:binding  [ rs:value     <http://purl.org/dc/elements/1.1/title> ;\n                          rs:variable  \"predicate\"\n                        ] ;\n            rs:binding  [ rs:value     <http://example/book1> ;\n                          rs:variable  \"subject\"\n                        ] ] ;\n  rs:solution        [ rs:binding  [ rs:value     \"A default book\" ;\n                                     rs:variable  \"object\"\n                                   ] ;\n            rs:binding  [ rs:value     <http://purl.org/dc/elements/1.1/title> ;\n                          rs:variable  \"predicate\"\n                        ] ;\n            rs:binding  [ rs:value     <http://example/book0> ;\n                          rs:variable  \"subject\"\n                        ] ]\n] .\n")

(expect model-ttl (model->ttl (result->model (->result (select-all)))))

(expect model-ttl
        (with-open [out (java.io.ByteArrayOutputStream.)]
          (.toString
           (model->ttl (result->model (->result (select-all))) out) "UTF-8")))

(expect true (not (nil? (result->xml (->result (select-all))))))

(def json-result "{\n  \"head\": {\n    \"vars\": [ \"subject\" , \"predicate\" , \"object\" ]\n  } ,\n  \"results\": {\n    \"bindings\": [\n      {\n        \"subject\": { \"type\": \"uri\" , \"value\": \"http://example/book0\" } ,\n        \"predicate\": { \"type\": \"uri\" , \"value\": \"http://purl.org/dc/elements/1.1/title\" } ,\n        \"object\": { \"type\": \"literal\" , \"value\": \"A default book\" }\n      } ,\n      {\n        \"subject\": { \"type\": \"uri\" , \"value\": \"http://example/book1\" } ,\n        \"predicate\": { \"type\": \"uri\" , \"value\": \"http://purl.org/dc/elements/1.1/title\" } ,\n        \"object\": { \"type\": \"literal\" , \"value\": \"A new book\" }\n      } ,\n      {\n        \"subject\": { \"type\": \"uri\" , \"value\": \"http://example/book2\" } ,\n        \"predicate\": { \"type\": \"uri\" , \"value\": \"http://purl.org/dc/elements/1.1/title\" } ,\n        \"object\": { \"type\": \"literal\" , \"value\": \"A second book\" }\n      } ,\n      {\n        \"subject\": { \"type\": \"uri\" , \"value\": \"http://example/book3\" } ,\n        \"predicate\": { \"type\": \"uri\" , \"value\": \"http://purl.org/dc/elements/1.1/title\" } ,\n        \"object\": { \"type\": \"literal\" , \"value\": \"A third book\" }\n      }\n    ]\n  }\n}\n")

(expect json-result (result->json (->result (select-all))))

(expect json-result
        (with-open [out (java.io.ByteArrayOutputStream.)]
          (.toString (result->json (->result (select-all)) out) "UTF-8")))

(defquery select-book
  "yesparql/samples/select-bindings.sparql"
  {:connection tdb})

;; Test with comments
(defquery select-foo
  "yesparql/samples/with-comments.sparql"
  {:connection tdb})

(expect
 [{"object" {:type "http://www.w3.org/2001/XMLSchema#string", :value "A default book"},
   "subject" "http://example/book0",
   "predicate" "http://purl.org/dc/elements/1.1/title"}
  {"object" {:type "http://www.w3.org/2001/XMLSchema#string", :value "A new book"},
   "subject" "http://example/book1",
   "predicate" "http://purl.org/dc/elements/1.1/title"}
  {"object" {:type "http://www.w3.org/2001/XMLSchema#string", :value "A second book"},
   "subject" "http://example/book2",
   "predicate" "http://purl.org/dc/elements/1.1/title"}
  {"object" {:type "http://www.w3.org/2001/XMLSchema#string", :value "A third book"},
   "subject" "http://example/book3",
   "predicate" "http://purl.org/dc/elements/1.1/title"}]
 (into [] (select-foo)))

;; Test remote SPARQL endpoints
(defquery dbpedia-select
  "yesparql/samples/remote-query.sparql"
  {:connection "http://dbpedia.org/sparql"})

(expect 10
        (triple-count
         (dbpedia-select
          {:timeout 500
           :bindings {"subject" (URI. "http://dbpedia.org/resource/Category:1952_deaths")}})))

;; Same thing but with prefixed uri and keyword
(expect 10
        (triple-count
         (dbpedia-select
          {:timeout 500
           :bindings {:subject (URI. "category:1952_deaths")}})))


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


(defquery with-arq "yesparql/samples/path.sparql"
  {:connection "http://dbpedia.org/sparql"})

(expect 5 (count (into [] (with-arq))))
