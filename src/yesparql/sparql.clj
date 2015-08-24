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

;; Util
(defn ^java.io.OutputStream output-stream []
  (java.io.ByteArrayOutputStream.))

(defn- reset-if-rewindable!
  [^ResultSet result]
  (when (instance? org.apache.jena.query.ResultSetRewindable result)
    (.reset result)))


;; Serialize model
(defn serialize-model
  [^Model model ^String format]
  (with-open [w (java.io.StringWriter.)]
    (.write model w format)
    (str w)))

(defn model->rdf+xml [^Model model] (serialize-model model "RDF/XML"))
(defn model->ttl [^Model model] (serialize-model model "TTL"))
(defn model->json-ld [^Model model] (serialize-model model "JSONLD"))


;; Serialize ResultSet
(defmacro serialize-result
  [method result]
  `(let [output# (output-stream)]
     (try
       (do
         (reset-if-rewindable! ~result)
         (~method ^java.io.OutputStream output# ^ResultSet ~result)
         (.toString output# "UTF-8"))
       (finally (.close output#)))))

(defn result->json* [result] (serialize-result ResultSetFormatter/outputAsJSON result))
(defn result->xml* [result] (serialize-result ResultSetFormatter/outputAsXML result))
(defn result->csv* [result] (serialize-result ResultSetFormatter/outputAsCSV result))
(defn result->tsv* [result] (serialize-result ResultSetFormatter/outputAsTSV result))

(defn result->clj*
  [^ResultSet result]
  (json/decode (result->json result) true))

(defn result->model*
  [^ResultSet result]
  (let [^RDFOutput rdf (RDFOutput.)]
    (reset-if-rewindable! result)
    (.asModel rdf result)))

(defprotocol Convert
  "A protocol for converting Result to several usable output formats"
  (result->json [this] "Result as JSON")
  (result->csv [this] "Result as CSV")
  (result->tsv [this] "Result as TSV")
  (result->clj [this] "Result as a Clojure map (via JSON)")
  (result->xml [this] "Result as XML (not RDF)")
  (result->model [this] "Convert result to a Jena `Model`"))

(defn- copy-result
  [record]
  (assoc record :result-set (ResultSetFactory/copyResults (:result-set record))))

(defrecord Result [query-exec result-set]
  Convert
  (result->json [this] (result->json* result-set))
  (result->csv [this] (result->csv*  result-set))
  (result->tsv [this] (result->tsv* result-set))
  (result->clj [this] (result->clj* result-set))
  (result->xml [this] (result->xml* result-set))
  (result->model [this] (result->model* result-set))
  java.io.Closeable
  (close [this] (.close query-exec)))

(defn parameterized-query
  [^String statement]
  (ParameterizedSparqlString. statement))

(defn ^ParameterizedSparqlString query-with-bindings
  "The `query` string will be formatted as a `ParameterizedSparqlString`
   and can be provided with a map of `bindings`.
   Each binding is a String->URL, String->URI, String->Node or String->RDFNode.
   Any other type (e.g. strings, float) will be set as literal.
   Does not warn if setting a binding that does not exist. "
  [^ParameterizedSparqlString pq bindings]
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
  pq)


(defmulti query-exec (fn [connection _] (class connection)))
(defmethod query-exec String [connection query]
  (QueryExecutionFactory/sparqlService
   ^String connection
   ^Query query))

(defn query-exec*
  [connection query]
  (QueryExecutionFactory/create
   ^Query query
   connection))

(defmethod query-exec Dataset [connection query] (query-exec* connection query))
(defmethod query-exec DatasetGraph [connection query] (query-exec* connection query))
(defmethod query-exec Model [connection query] (query-exec* connection query))

;; TODO maybe use a macro for this?
(defmulti query* (fn [q-exec type] type))
(defmethod query* "ask" [q-exec _] (.execAsk  ^QueryExecution q-exec))
(defmethod query* "construct" [q-exec _] (.execConstruct ^QueryExecution q-exec))
(defmethod query* "describe" [q-exec _] (.execDescribe ^QueryExecution q-exec))
(defmethod query* "select" [q-exec _] (.execSelect ^QueryExecution q-exec))

(defn query
  [connection ^ParameterizedSparqlString pq {:keys [bindings timeout]}]
  (let [^Query q (.asQuery (query-with-bindings pq bindings))
        ^QueryExecution query-execution (query-exec connection q)
        query-type (cond
                     (.isSelectType q) "select"
                     (.isConstructType q) "construct"
                     (.isAskType q) "ask"
                     (.isDescribeType q) "describe")]
    (when timeout (.setTimeout query-execution timeout))
    (Result. query-execution (query* query-execution query-type))))

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
  `connection` can be a String for a SPARQL endpoint URL or `Dataset`"
  [connection ^ParameterizedSparqlString pq {:keys [bindings]}]
  (let [q (.toString (.asUpdate (query-with-bindings pq bindings)))
        ^UpdateRequest update-request (UpdateFactory/create q)
        ^UpdateProcessor processor (update-exec connection update-request)]
    (.execute processor)))
