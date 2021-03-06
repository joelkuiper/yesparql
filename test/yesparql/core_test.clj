(ns yesparql.core-test
  (:import [java.net URI URL URLEncoder])
  (:require [clojure.string :refer [upper-case trim-newline]]
            [expectations :refer :all]
            [clojure.java.io :as io]
            [yesparql.tdb :as tdb]
            [yesparql.util :as yu]
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

;; Test with transactions
(def tdb2 (tdb/create-in-memory))

(defquery select-all*
  "yesparql/samples/select.sparql"
  {:connection tdb2})

(defquery update-books*!
  "yesparql/samples/update.sparql"
  {:connection tdb2})


(with-transaction tdb2 :write
  (do
    (expect 0
            (triple-count (select-all*)))
    (expect nil (update-books*!))
    (expect 4
            (triple-count (select-all*)))))



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
(def model-ttl (trim-newline (yu/slurp-from-classpath "yesparql/output/model.ttl")))

(expect model-ttl (trim-newline (model->ttl (result->model (->result (select-all))))))

(expect model-ttl
        (with-open [out (java.io.ByteArrayOutputStream.)]
          (trim-newline
           (.toString
            (model->ttl (result->model (->result (select-all))) out) "UTF-8"))))

(expect true (not (nil? (result->xml (->result (select-all))))))

(def json-result (trim-newline (yu/slurp-from-classpath "yesparql/output/result.json")))

(expect json-result (trim-newline (result->json (->result (select-all)))))

(expect json-result
        (with-open [out (java.io.ByteArrayOutputStream.)]
          (trim-newline
           (.toString (result->json (->result (select-all)) out) "UTF-8"))))

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

(defquery update-books-param!
  "yesparql/samples/update-params.sparql"
  {:connection tdb})

(expect 5 (do
            (update-books-param! {:bindings {:title "an awesome new book"}})
            (triple-count (select-all))))

(expect
 [{"object" {:type "http://www.w3.org/2001/XMLSchema#string", :value "an awesome new book"},
   "subject" "http://example/book5",
   "predicate" "http://purl.org/dc/elements/1.1/title"}]
 (into [] (select-all {:limit 1 :offset 4})))

;; Test remote SPARQL endpoints
(defquery dbpedia-select
  "yesparql/samples/remote-query.sparql"
  {:connection "http://dbpedia.org/sparql"})

(expect 10
        (triple-count
         (dbpedia-select
          {:timeout 1500
           :bindings {"subject" (URI. "http://dbpedia.org/resource/Category:1952_deaths")}})))

;; Same thing but with prefixed uri and keyword
(expect 10
        (triple-count
         (dbpedia-select
          {:timeout 1500
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

;; Test limit
(expect 5 (count (into [] (select-all))))
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

