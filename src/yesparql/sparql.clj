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

(defn result->json ([result] (serialize-result ResultSetFormatter/outputAsJSON result)))
(defn result->xml ([result] (serialize-result ResultSetFormatter/outputAsXML result)))
(defn result->csv ([result] (serialize-result ResultSetFormatter/outputAsCSV result)))
(defn result->tsv ([result] (serialize-result ResultSetFormatter/outputAsTSV result)))

(defn result->clj
  ([result]
   (json/decode (result->json ^ResultSet result) true)))

(defn result->model
  ([result]
   (let [^RDFOutput rdf (RDFOutput.)]
     (reset-if-rewindable! result)
     (.asModel rdf ^ResultSet result))))

(defn copy-result
  [^ResultSet result]
  (ResultSetFactory/copyResults result))

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

(defn- query-type
  [q]
  (cond
    (.isSelectType q) "select"
    (.isConstructType q) "construct"
    (.isAskType q) "ask"
    (.isDescribeType q) "describe"))

;; TODO maybe use a macro or a protocol for this?
(defmulti query* (fn [query _] (query-type query)))
(defmethod query* "ask" [query q-exec] (.execAsk  ^QueryExecution q-exec))
(defmethod query* "construct" [query q-exec] (.execConstruct ^QueryExecution q-exec))
(defmethod query* "describe" [query q-exec] (.execDescribe ^QueryExecution q-exec))
(defmethod query* "select" [query q-exec] (.execSelect ^QueryExecution q-exec))



(defn query
  [connection ^ParameterizedSparqlString pq {:keys [bindings timeout]}]
  (let [^Query q (.asQuery (query-with-bindings pq bindings))
        ^QueryExecution query-execution (query-exec connection q)]
    (when timeout (.setTimeout query-execution timeout))
    [query-execution (query* q query-execution)]))

(defmacro with-query-exec
  [bindings & body]
  `(let [[q-exec# ~(first bindings)] ~(second bindings)]
     (with-open [qe# q-exec#]
       ~@body)))


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
