(ns yesparql.sparql
  (:refer-clojure :exclude [update])
  (:import
   [java.lang.IllegalArgumentException]
   [java.net URL URI]
   [org.apache.jena.graph Node]
   [org.apache.jena.update
    Update UpdateAction
    UpdateFactory UpdateProcessor
    UpdateRequest UpdateExecutionFactory]
   [org.apache.jena.rdf.model Model
    StmtIterator Statement Resource Property
    RDFNode Resource Literal]
   [org.apache.jena.shared PrefixMapping]
   [org.apache.jena.query Dataset]
   [org.apache.jena.sparql.resultset RDFOutput]
   [org.apache.jena.rdf.model ModelFactory]
   [org.apache.jena.datatypes BaseDatatype]
   [org.apache.jena.graph Node Node_Literal Node_Blank Node_NULL Node_URI]
   [org.apache.jena.sparql.core Var ResultBinding]
   [org.apache.jena.query
    Query QuerySolution QueryExecution Syntax
    QueryExecutionFactory QueryFactory QuerySolutionMap
    ParameterizedSparqlString ResultSetFactory
    ResultSetRewindable ResultSet ResultSetFormatter]))

(defn ^java.io.ByteArrayOutputStream output-stream []
  (java.io.ByteArrayOutputStream.))

(defn reset-if-rewindable!
  "Resets a `RewindableResulSet`

   See: [ResultSetRewindable](https://jena.apache.org/documentation/javadoc/arq/org/apache/jena/query/ResultSetRewindable.html)."
  [^ResultSet result]
  (when (instance? ResultSetRewindable result)
    (.reset ^ResultSetRewindable result)))

(defn falsey-string
  "JavaScript-ism to return nil on an empty string."
  [str]
  (when (seq str) str))

(defn copy-result-set
  "Returns a copy of a `ResultSet` allowing it to be re-used.

  Make sure to apply this function if you intend to re-use the
  `ResultSet` after initial traversal.

  See also: `reset-if-rewindable!`"
  [^ResultSet result]
  (ResultSetFactory/copyResults result))

(defn serialize-model
  "Serializes a `Model` to a string or to an output stream

   See: [Jena Model Write formats](https://jena.apache.org/documentation/io/rdf-output.html#jena_model_write_formats)."
  ([^Model model ^String format ^java.io.OutputStream out]
   (.write model out format) out)
  ([^Model model ^String format]
   (with-open [out (output-stream)]
     (serialize-model model format out)
     (.toString out "UTF-8"))))

(defn model->rdf+xml
  ([^Model model]
   (serialize-model model "RDF/XML"))
  ([^Model model ^java.io.OutputStream out]
   (serialize-model model "RDF/XML" out)))

(defn model->ttl
  ([^Model model]
   (serialize-model model "TTL"))
  ([^Model model ^java.io.OutputStream out]
   (serialize-model model "TTL" out)))

(defn model->json-ld
  ([^Model model]
   (serialize-model model "JSONLD"))
  ([^Model model ^java.io.OutputStream out]
   (serialize-model model "JSONLD" out)))

(defmacro serialize-result
  "Serializes a `Result` to a string"
  ([method result output-stream]
   `(do
      (reset-if-rewindable! ~result)
      (~method ^java.io.OutputStream ~output-stream ^ResultSet ~result)
      ~output-stream))
  ([method result]
   `(let [output# (output-stream)]
      (try
        (do
          (serialize-result ~method ~result output#)
          (.toString output# "UTF-8"))
        (finally (.close output#))))))

(defn result->json
  ([^ResultSet result]
   (serialize-result ResultSetFormatter/outputAsJSON result))
  ([^ResultSet result ^java.io.OutputStream out]
   (serialize-result ResultSetFormatter/outputAsJSON result out)))

(defn result->text
  ([^ResultSet result]
   (ResultSetFormatter/asText result))
  ([^ResultSet result ^java.io.OutputStream out]
   (ResultSetFormatter/asText result out)))

(defn result->xml
  ([^ResultSet result]
   (serialize-result ResultSetFormatter/outputAsXML result))
  ([^ResultSet result ^java.io.OutputStream out]
   (serialize-result ResultSetFormatter/outputAsXML result out)))

(defn result->csv
  ([^ResultSet result]
   (serialize-result ResultSetFormatter/outputAsCSV result))
  ([^ResultSet result ^java.io.OutputStream out]
   (serialize-result ResultSetFormatter/outputAsCSV result out)))

(defn result->tsv
  ([^ResultSet result]
   (serialize-result ResultSetFormatter/outputAsTSV result))
  ([^ResultSet result ^java.io.OutputStream out]
   (serialize-result ResultSetFormatter/outputAsTSV result out)))

(defn result->model
  "Converts `ResultSet` to a `Model`.

   NOTE: CONSTRUCT and DESCRIBE queries are better suited for conversion to `Model`."
  [^ResultSet result]
  (let [^RDFOutput rdf (RDFOutput.)]
    (reset-if-rewindable! result)
    (.asModel rdf ^ResultSet result)))

(def ^Model default-model (ModelFactory/createDefaultModel))
(def ^Syntax default-syntax Syntax/defaultSyntax)

(defn keyword-str [kw] (if (keyword? kw) (name kw) kw))

(defn ^Literal clj->literal
  [{:keys [value type lang]}]
  (cond
    type (.createTypedLiteral
          default-model value (BaseDatatype. ^String (str type)))
    lang (.createLiteral
          default-model ^String (str value) ^String (keyword-str lang))
    :else (.createTypedLiteral
           default-model value)))

(defn ^ParameterizedSparqlString parameterized-query
  [^String statement]
  (ParameterizedSparqlString. statement))

(defn ^ParameterizedSparqlString query-with-bindings
  "The `query` can be provided with a map of `bindings`.
   Each binding is a String->URL, String->URI, String->Node or String->RDFNode.
   Any other type (e.g. String, Float) will be set as Literal.

   Alternatively, you can supply a map of
   `{:type (optional, uri or string), :lang (optional, str or keyword), :value}`
   which will be coerced to the appropriate `Literal` automatically.

   Does not warn when setting a binding that does not exist."
  [^ParameterizedSparqlString pq ^PrefixMapping prefixes bindings]
  (doall
   (map
    (fn [[var resource]]
      (let [^String subs
            (cond
              (string? var) var
              (keyword? var) (name var)
              :else
              (throw (java.lang.IllegalArgumentException.
                      "ParameterizedSparqlString binding keys must be strings, keywords or integers.")))]
        (if (map? resource)
          (.setLiteral pq ^String subs ^Literal (clj->literal resource))
          (condp instance? resource
            URL (.setIri pq subs ^URL resource)
            URI (.setIri pq subs ^String (.expandPrefix prefixes (str resource)))
            Node (.setParam pq subs ^Node resource)
            RDFNode (.setParam pq subs ^RDFNode resource)
            (.setLiteral pq subs resource)))))
    bindings))
  pq)

(defn with-type
  [f ^Node_Literal literal]
  (if-let [lang (falsey-string (.getLiteralLanguage literal))]
    {:type (.getLiteralDatatypeURI literal)
     :value (f literal)
     :lang (keyword lang)}
    {:type (.getLiteralDatatypeURI literal)
     :value (f literal)}))

(defmulti node->clj (fn [^Node_Literal literal] (.getLiteralDatatypeURI literal)))

(defmethod node->clj nil [^Node_Literal literal]
  {:value (.getLiteralValue literal)})

(defmethod node->clj :default [^Node_Literal literal]
  (try
    (with-type #(.getLiteralValue ^Node_Literal %) literal)
    (catch org.apache.jena.shared.JenaException e
      {:value (.getLiteralLexicalForm literal)
       :type (.getLiteralDatatypeURI literal)})))

(defprotocol INodeConvertible
  (convert [^Node this]))

(extend-protocol INodeConvertible
  org.apache.jena.graph.Node_Blank
  (convert [this] (.getBlankNodeId this))
  org.apache.jena.graph.Node_Literal
  (convert [this] (node->clj this))
  org.apache.jena.graph.Node_NULL
  (convert [this] nil)
  org.apache.jena.graph.Node_URI
  (convert [this] (.getURI this)))

(defn result-binding->clj
  [^ResultBinding result-binding]
  (let [binding (.getBinding result-binding)]
    (into {} (map (fn [^Var v] [(.getVarName v) (convert (.get binding v))])
                  (iterator-seq (.vars binding))))))

(deftype CloseableResultSet [^QueryExecution qe ^ResultSet rs]
  clojure.lang.Seqable
  (seq [_]
    (when-let [iseq (seq (iterator-seq rs))]
      (map result-binding->clj iseq)))
  java.lang.AutoCloseable
  (close [_] (.close qe)))

(defn ->query-execution
  "Returns the underlying `QueryExecution` from the query results"
  [r] (.qe r))

(defn ->result
  "Returns the underlying `ResultSet` from the query results

  See also: `copy-result-set` for a re-usable ResultSet"
  [^CloseableResultSet r]
  (.rs r))

(defrecord Triple [s p o])

(defn triple->clj
  [^org.apache.jena.graph.Triple t]
  (apply
   ->Triple
   (map convert [(.getSubject t) (.getPredicate t) (.getObject t)])))

(defn statement->clj
  [^Statement s]
  (triple->clj s))

(deftype CloseableModel [^QueryExecution qe ^java.util.Iterator t]
  clojure.lang.Seqable
  (seq [this]
    (when-let [iseq (seq (iterator-seq t))]
      (map statement->clj iseq)))
  java.lang.AutoCloseable
  (close [this] (.close qe)))

(defn ->model
  "Generate as `Model` from the stream of `Triple`.
   The stream is consumed in the process, and cannot be traversed again.

   NOTE: closes the underlying `QueryExecution`."
  [^CloseableModel closeable-model]
  (with-open [model closeable-model]
    (let [^Model m
          (ModelFactory/createDefaultModel)
          ^java.util.List statements
          (java.util.ArrayList.
           ^java.util.Collection
           (doall (map #(.asStatement m %) (iterator-seq (.t model)))))]
      (.add m statements)
      m)))

(defn ->triples
  "Returns the unconverted Jena Iterator of `org.apache.jena.graph.Triple`"
  [^CloseableModel m]
  (.t m))

(defmulti query-exec (fn [connection _] (class connection)))
(defmethod query-exec String [connection query]
  (QueryExecutionFactory/sparqlService
   ^String connection
   ^Query query))

(defmethod query-exec Dataset [connection query]
  (QueryExecutionFactory/create ^Query query ^Dataset connection))
(defmethod query-exec Model [connection query]
  (QueryExecutionFactory/create ^Query query ^Model connection))

(defn query-type
  [^Query q]
  (cond
    (.isSelectType q) "execSelect"
    (.isConstructType q) "execConstructTriples"
    (.isAskType q) "execAsk"
    (.isDescribeType q) "execDescribeTriples"))

(defmulti query*
  (fn [^QueryExecution q-exec] (query-type (.getQuery q-exec))))
(defmethod query* "execSelect" [^QueryExecution q-exec]
  (.execSelect q-exec))
(defmethod query* "execAsk" [^QueryExecution q-exec]
  (.execAsk q-exec))
(defmethod query* "execConstructTriples" [^QueryExecution q-exec]
  (.execConstructTriples q-exec))
(defmethod query* "execDescribeTriples" [^QueryExecution q-exec]
  (.execDescribeTriples q-exec))

(defn ^Query as-query
  ([^String qstr]
   (as-query Syntax/defaultQuerySyntax qstr))
  ([^Syntax syntax ^String qstr]
   (QueryFactory/create qstr syntax)))

(defn ->execution
  [connection ^ParameterizedSparqlString pq ^Syntax syntax {:keys [bindings timeout]}]
  (let [^String qstr (str pq)
        ^Query q (as-query syntax qstr)
        ^QueryExecution query-execution (query-exec connection q)]
    (when timeout (.setTimeout query-execution timeout))
    query-execution))

(defn set-additional-fields
  [^Query query call-options]
  (do
    (when-let [offset (:offset call-options)]
      (.setOffset query (long offset)))
    (when-let [limit (:limit call-options)]
      (.setLimit query (long limit)))
    query))


(defn query
  "Executes a SPARQL SELECT, ASK, DESCRIBE or CONSTRUCT based on the
  query type against the `connection`. `connection` can be a String
  for a SPARQL endpoint URL or `Dataset`, `Model`.

   Returns a lazy-seq of results that can be traversed iteratively.
  SELECT returns a seq of `ResultBinding`s in a native Clojure format.
  DESCRIBE and CONSTRUCT return a seq of Triples (s, p, o) in a native
  Clojure format. ASK returns a boolean.

   See also: `->result` (SELECT), `->model` (DESCRIBE, CONSTRUCT) and
  `->query-execution`. Or use the `result->csv`..., and `model->json-ld`
  convenience methods for serialization to strings.

   WARNING: The underlying `QueryExecution` must be closed in order to
  prevent resources from leaking. Call the query in a `with-open` or
  close manually with `(.close (->query-execution (query)))`. "
  ([connection
    ^PrefixMapping prefixes
    ^ParameterizedSparqlString pq
    options]
   (query connection prefixes default-syntax pq options))
  ([connection
    ^PrefixMapping prefixes
    ^Syntax syntax
    ^ParameterizedSparqlString pq
    {:keys [bindings timeout] :as call-options}]
   (let [pq (query-with-bindings pq prefixes bindings)
         query-execution
         (->execution connection pq syntax call-options)
         query
         (set-additional-fields
          (.getQuery ^QueryExecution query-execution) call-options)
         query-type
         (query-type query)]
     (cond
       (= query-type "execSelect")
       (->CloseableResultSet query-execution (query* query-execution))
       (or (= query-type "execDescribeTriples")
           (= query-type "execConstructTriples"))
       (->CloseableModel query-execution (query* query-execution))
       :else
       (try (query* query-execution)
            (finally (.close ^QueryExecution query-execution)))))))

(defmulti update-exec (fn [connection _] (class connection)))
(defmethod update-exec String [connection update]
  (UpdateExecutionFactory/createRemote ^UpdateRequest update ^String connection))
(defmethod update-exec Dataset [connection update]
  (UpdateExecutionFactory/create ^UpdateRequest update ^Dataset connection))

(defn ^UpdateRequest as-update
  ([^String ustr]
   (as-update Syntax/defaultUpdateSyntax ustr))
  ([^Syntax syntax ^String qstr]
   (UpdateFactory/create qstr syntax)))

(defn update!
  "Execute a SPARQL UPDATE `query` against the `connection`,
  returning nil if success, throw an exception otherwise. `bindings`
  will be substituted when possible, can be empty.
  `connection` can be a String for a SPARQL endpoint URL or `Dataset`.

  Returns nil on success, or throws an Exception. "
  ([connection
    ^PrefixMapping prefixes
    ^ParameterizedSparqlString pq
    options]
   (update! connection prefixes default-syntax pq options))
  ([connection
    ^PrefixMapping prefixes
    ^Syntax syntax
    ^ParameterizedSparqlString pq
    {:keys [bindings]}]
   (let [ustr (str (query-with-bindings pq prefixes bindings))
         update-request (as-update syntax ustr)
         processor (update-exec connection update-request)]
     (.execute ^UpdateProcessor processor))))
