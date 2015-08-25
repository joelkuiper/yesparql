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

(defn result->json [result] (serialize-result ResultSetFormatter/outputAsJSON result))
(defn result->xml [result] (serialize-result ResultSetFormatter/outputAsXML result))
(defn result->csv [result] (serialize-result ResultSetFormatter/outputAsCSV result))
(defn result->tsv [result] (serialize-result ResultSetFormatter/outputAsTSV result))

(defn result->clj
  ([result]
   (json/decode (result->json ^ResultSet result) true)))

(defn result->model
  ([result]
   (let [^RDFOutput rdf (RDFOutput.)]
     (reset-if-rewindable! result)
     (.asModel rdf ^ResultSet result))))

(defn copy-result
  [result]
  (ResultSetFactory/copyResults result))

(defn ^ParameterizedSparqlString parameterized-query
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
  [^Query q]
  (cond
    (.isSelectType q) "execSelect"
    (.isConstructType q) "execConstruct"
    (.isAskType q) "execAsk"
    (.isDescribeType q) "execDescribe"))

;; TODO maybe use a macro or a protocol for this?
(defmulti query* (fn [q-exec] (query-type (.getQuery q-exec))))
(defmethod query* "execAsk" [q-exec] (.execAsk  ^QueryExecution q-exec))
(defmethod query* "execConstruct" [q-exec] (.execConstruct ^QueryExecution q-exec))
(defmethod query* "execDescribe" [q-exec] (.execDescribe ^QueryExecution q-exec))
(defmethod query* "execSelect" [q-exec] (.execSelect ^QueryExecution q-exec))

(defn ->execution
  [connection ^ParameterizedSparqlString pq {:keys [bindings timeout]} & [with-query-exec?]]
  (let [^Query q (.asQuery pq)
        ^QueryExecution query-execution (query-exec connection q)]
    (when timeout (.setTimeout query-execution timeout))
    query-execution))

(defn query
  [connection ^ParameterizedSparqlString pq {:keys [bindings timeout] :as call-options} & [with-query-exec?]]
  (let [query-execution (->execution connection (query-with-bindings pq bindings) call-options)]
    (if with-query-exec?
      ;; This is the case for when called from within the `with-query-execution` macro.
      ;; In this case we return a tuple of [`QueryExecution` result].
      ;; The type of the second element, result, depends on the type of executed query.
      ;; and is one of {`ResultSet`, `Model`, `boolean`}.
      ;; In the case of a `ResultSet` a user can process it iteratively, as a stream.
      [query-execution (query* query-execution)]

      ;; This is the default case and will consume the entire result
      ;; and subsequently close the `QueryExecution`.
      ;; This prevents resources from leaking, but is more memory intensive.
      ;; When the query result is a `ResultSet` we return a copy.
      (with-open [q-exec query-execution]
        (if (= (query-type (.getQuery ^QueryExecution q-exec)) "execSelect")
          (copy-result (query* query-execution))
          (query* query-execution))))))


(defmacro add-arg
  [fun arg]
  (concat fun (cons arg '())))

(defmacro with-query-execution
  [binding & body]
  `(let [[q-exec#  ~(first binding)] (add-arg ~(second binding) :with-query-exec?)]
     (with-open [qe# q-exec#]
       ~@body)))


(defmulti update-exec (fn [connection _] (class connection)))
(defmethod update-exec String [connection update]
  (UpdateExecutionFactory/createRemote update ^String connection))
(defmethod update-exec Dataset [connection update]
  (UpdateExecutionFactory/create update ^Dataset connection))
(defmethod update-exec DatasetGraph [connection update]
  (UpdateExecutionFactory/create update ^DatasetGraph connection))

(defn update
  "Execute a SPARQL UPDATE `query` against the `connection`,
  returning nil if success, throw an exception otherwise. `bindings`
  will be substituted when possible, can be empty.
  `connection` can be a String for a SPARQL endpoint URL or `Dataset`"
  [connection ^ParameterizedSparqlString pq {:keys [bindings]} & args]
  (let [q (.toString (.asUpdate (query-with-bindings pq bindings)))
        ^UpdateRequest update-request (UpdateFactory/create q)
        ^UpdateProcessor processor (update-exec connection update-request)]
    (.execute processor)))
