(ns yesparql.repl
  (:import [java.net URI URL URLEncoder])
  (:require
   [yesparql.sparql :refer :all]
   [yesparql.core :refer :all]))


;; Define the query by point to the file
(defquery select-intellectuals
  "yesparql/samples/select-intellectuals.sparql"
  {:connection "http://dbpedia.org/sparql"})


;; Just run the query
(defn all []
  (with-open
    [result (select-intellectuals)]
    (into [] result)))

(clojure.pprint/print-table (all))

;; Run the query with some bindings
;; In this case we want all the intellectuals that were
;; Dutch Mathematicians
(defn dutch-mathematicians []
  (with-open
    [result
     (select-intellectuals
      {:bindings
       {"subject"
        (URI. "http://dbpedia.org/resource/Category:Dutch_mathematicians")}})]
    (into [] result)))

(clojure.pprint/print-table (dutch-mathematicians))


;;; Scratch
(def s (yesparql.util/slurp-from-classpath "yesparql/samples/select-intellectuals.sparql"))

(def pq (parameterized-query s))

(def q (.asQuery pq))
