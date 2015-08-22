(ns yesparql.sparql
  (:refer-clojure :exclude [update])
  (:require [cheshire.core :as json])
  (:import
   [java.net URL URI]
   [org.apache.jena.graph Node]
   [org.apache.jena.update
    Update UpdateAction
    UpdateFactory UpdateProcessor
    UpdateRequest UpdateExecutionFactory]
   [org.apache.jena.rdf.model Model
    RDFNode Resource Literal]
   [org.apache.jena.query Dataset]
   [org.apache.jena.sparql.core DatasetGraph]
   [org.apache.jena.query
    Query QuerySolution QueryExecution
    QueryExecutionFactory QueryFactory QuerySolutionMap
    ParameterizedSparqlString
    ResultSetFactory ResultSet ResultSetFormatter]))

(defn query-with-bindings
  [query bindings]
  (let [pq (ParameterizedSparqlString. ^String query)]
    (doall
     (map
      (fn [[name resource]]
        (condp instance? resource
          URL (.setIri pq ^String name ^URL resource)
          URI (.setIri pq ^String name ^String (str resource))
          Node (.setParam pq ^String name ^Node resource)
          RDFNode (.setParam pq ^String name ^RDFNode resource)
          (.setLiteral pq name resource)))
      bindings))
    pq))

(defmulti query-exec (fn [data-set _ _] (class data-set)))

(defmethod query-exec String [data-set query bindings]
  (QueryExecutionFactory/sparqlService
   ^String data-set
   ^Query (.asQuery (query-with-bindings query bindings))))

(defn exec*
  [data-set query bindings]
  (QueryExecutionFactory/create
   ^Query (.asQuery (query-with-bindings query bindings))
   ^Dataset data-set))

(defmethod query-exec Dataset [data-set query bindings] (exec* data-set query bindings))
(defmethod query-exec DatasetGraph [data-set query bindings] (exec* data-set query bindings))
(defmethod query-exec Model [data-set query bindings] (exec* data-set query bindings))

(defn select
  [data-set ^String query bindings]
  (with-open [q ^QueryExecution (query-exec data-set query bindings)]
    (ResultSetFactory/copyResults (.execSelect q))))

(defn construct
  [data-set ^String query bindings]
  (with-open [q ^QueryExecution (query-exec data-set query bindings)]
    (.execConstruct q)))

(defn describe
  [data-set ^String query bindings]
  (with-open [q ^QueryExecution (query-exec data-set query bindings)]
    (.execDescribe q)))

(defn ask
  [data-set ^String query bindings]
  (with-open [q ^QueryExecution (query-exec data-set query bindings)]
    (.execAsk q)))

(defmulti update-exec (fn [data-set _] (class data-set)))
(defmethod update-exec String [data-set update]
  (UpdateExecutionFactory/createRemote update ^String data-set))
(defmethod update-exec Dataset [data-set update]
  (UpdateExecutionFactory/create update ^Dataset data-set))
(defmethod update-exec DatasetGraph [data-set update]
  (UpdateExecutionFactory/create update ^DatasetGraph data-set))


(defn update
  ([data-set updates]
   (let [^UpdateRequest update-request (UpdateFactory/create)]
     (doseq [update updates]
       (.add update-request ^String update))
     (.execute (update-exec data-set update-request))))
  ([data-set ^String query bindings]
   (let [q (.toString (query-with-bindings query bindings))
         ^UpdateRequest update-request (UpdateFactory/create q)
         ^UpdateProcessor processor (update-exec data-set update-request)]
     (.execute processor))))

(defn output-stream []
  (java.io.ByteArrayOutputStream.))

;; Convert ResultSet
(defn result->json
  [^ResultSet result]
  (let [^java.io.OutputStream out (output-stream)]
    (ResultSetFormatter/outputAsJSON out result)
    (str out)))

(defn result->csv
  [^ResultSet result]
  (let [^java.io.OutputStream out (output-stream)]
    (ResultSetFormatter/outputAsCSV out result)
    (str out)))

(defn result->xml
  [^ResultSet result]
  (let [^java.io.OutputStream out (output-stream)]
    (ResultSetFormatter/outputAsXML out result)
    (str out)))

(defn result->clj
  [^ResultSet result]
  (json/decode (result->json result) true))

;; Convert model
(defn serialize-model
  [^Model model ^String lang]
  (with-open [w (java.io.StringWriter.)]
    (.write model w lang)
    (str w)))

(defn model->rdf+xml [^Model model] (serialize-model model "RDF/XML"))
(defn model->ttl [^Model model] (serialize-model model "TTL"))
(defn model->json-ld [^Model model] (serialize-model model "JSONLD"))
