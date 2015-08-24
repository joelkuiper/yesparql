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
   [org.apache.jena.sparql.resultset RDFOutput]
   [org.apache.jena.query
    Query QuerySolution QueryExecution
    QueryExecutionFactory QueryFactory QuerySolutionMap
    ParameterizedSparqlString
    ResultSetFactory ResultSet ResultSetFormatter]))

(defn parameterize
  "The `query` string will be formatted as a `ParameterizedSparqlString`
   and can be provided with a map of `bindings`.
   Each binding is a String->URL, String->URI, String->Node or String->RDFNode.
   Any other type (e.g. strings, float) will be set as literal.
   Does not warn if setting a binding that does not exist. "
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
   ^Query (.asQuery (parameterize query bindings))))

(defn query-exec*
  [data-set query bindings]
  (QueryExecutionFactory/create
   ^Query (.asQuery (parameterize query bindings))
   ^Dataset data-set))

(defmethod query-exec Dataset [data-set query bindings] (query-exec* data-set query bindings))
(defmethod query-exec DatasetGraph [data-set query bindings] (query-exec* data-set query bindings))
(defmethod query-exec Model [data-set query bindings] (query-exec* data-set query bindings))

(defn select
  "Execute a SPARQL SELECT `query` against the `data-set`, returning a
  `ResultSet`. `bindings` will be substituted when possible, can be
  empty. `data-set` can be a String for a SPARQL endpoint URL or
  `Dataset`"
  [data-set ^String query {:keys [bindings timeout]}]
  (with-open [q ^QueryExecution (query-exec data-set query bindings)]
    (when timeout (.setTimeout q timeout))
    (ResultSetFactory/copyResults (.execSelect q))))

(defn construct
  "Execute a SPARQL CONSTRUCT `query` against the `data-set`,
  returning a `Model`. `bindings` will be substituted when possible,
  can be empty. `data-set` can be a String for a SPARQL endpoint URL
  or `Dataset`"
  [data-set ^String query {:keys [bindings timeout]}]
  (with-open [q ^QueryExecution (query-exec data-set query bindings)]
    (when timeout (.setTimeout q timeout))
    (.execConstruct q)))

(defn describe
  "Execute a SPARQL DESCRIBE `query` against the `data-set`, returning
  a `Model`. `bindings` will be substituted when possible, can be
  empty. `data-set` can be a String for a SPARQL endpoint URL or
  `Dataset`"
  [data-set ^String query {:keys [bindings timeout]}]
  (with-open [q ^QueryExecution (query-exec data-set query bindings)]
    (when timeout (.setTimeout q timeout))
    (.execDescribe q)))

(defn ask
  "Execute a SPARQL ASK `query` against the `data-set`, returning a
  boolean. `bindings` will be substituted when possible, can be empty.
  `data-set` can be a String for a SPARQL endpoint URL or `Dataset`"
  [data-set ^String query {:keys [bindings timeout]}]
  (with-open [q ^QueryExecution (query-exec data-set query bindings)]
    (when timeout (.setTimeout q timeout))
    (.execAsk q)))

(defmulti update-exec (fn [data-set _] (class data-set)))
(defmethod update-exec String [data-set update]
  (UpdateExecutionFactory/createRemote update ^String data-set))
(defmethod update-exec Dataset [data-set update]
  (UpdateExecutionFactory/create update ^Dataset data-set))
(defmethod update-exec DatasetGraph [data-set update]
  (UpdateExecutionFactory/create update ^DatasetGraph data-set))

(defn update
  "Execute a SPARQL UPDATE `query` against the `data-set`,
  returning nil if success, throw an exception otherwise. `bindings`
  will be substituted when possible, can be empty.
  `data-set` can be a String for a SPARQL endpoint URL or `Dataset`"
  [data-set ^String query {:keys [bindings]}]
  (let [q (.toString (.asUpdate (parameterize query bindings)))
        ^UpdateRequest update-request (UpdateFactory/create q)
        ^UpdateProcessor processor (update-exec data-set update-request)]
    (.execute processor)))

(defn output-stream []
  (java.io.ByteArrayOutputStream.))

(defn- reset-if-rewindable!
  [result]
  (when (instance? org.apache.jena.query.ResultSetRewindable result)
    (.reset result)))

;; Serialize ResultSet
(defmacro serialize-result
  [method result]
  `(let [output# (output-stream)]
     (try
       (do
         (reset-if-rewindable! ~result)
         (~method ^java.io.OutputStream output# ^ResultSet ~result)
         (str output#))
       (finally (.close output#)))))

(defn result->json [result] (serialize-result ResultSetFormatter/outputAsJSON result))
(defn result->xml [result] (serialize-result ResultSetFormatter/outputAsXML result))
(defn result->csv [result] (serialize-result ResultSetFormatter/outputAsCSV result))
(defn result->tsv [result] (serialize-result ResultSetFormatter/outputAsTSV result))

(defn result->clj
  [^ResultSet result]
  (json/decode (result->json result) true))

(defn result->model
  [^ResultSet result]
  (let [^RDFOutput rdf (RDFOutput.)]
    (reset-if-rewindable! result)
    (.asModel rdf result)))

;; Serialize model
(defn serialize-model
  [^Model model ^String format]
  (with-open [w (java.io.StringWriter.)]
    (.write model w format)
    (str w)))

(defn model->rdf+xml [^Model model] (serialize-model model "RDF/XML"))
(defn model->ttl [^Model model] (serialize-model model "TTL"))
(defn model->json-ld [^Model model] (serialize-model model "JSONLD"))
