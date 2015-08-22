(ns yesparql.core-test
  (:require [clojure.string :refer [upper-case]]
            [expectations :refer :all]

            [yesparql.jena :as jena]
            [yesparql.sparql :as sparql]
            [yesparql.core :refer :all]))

;; Test in-memory SPARQL


(def tdb (jena/create-in-mem-tdb))

(defquery update-with-books
  "yesparql/samples/update.sparql"
  {:connection tdb})



;; Test remote SPARQL endpoints

(defquery dbpedia-select
  "yesparql/samples/remote-query.sparql"
  {:connection "http://dbpedia.org/sparql"})


(expect 10
        (count (get-in
                (sparql/result->clj (dbpedia-select))
                [:results :bindings])))
