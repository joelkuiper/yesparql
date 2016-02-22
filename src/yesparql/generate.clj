(ns yesparql.generate
  (:require
   [clojure.set :as set]
   [clojure.string :refer [join lower-case]]
   [yesparql.util :refer [create-root-var]]
   [yesparql.sparql :as sparql]
   [yesparql.types :refer [map->Query]])
  (:import [yesparql.types Query]
           [org.apache.jena.shared PrefixMapping]
           [org.apache.jena.query ParameterizedSparqlString Syntax]))

(defn query-type
  [^String name]
  (cond
    (= (last name) \!) :update
    :else :query))

(defn statement-handler
  [^String name ^String filename ^String statement]
  (let [syntax (Syntax/guessFileSyntax filename)
        query (sparql/parameterized-query statement)
        query-type (query-type name)
        sparql-fn
        (case query-type
          :update sparql/update!
          :query sparql/query)
        ;; So this is a bit of a hack, but we convert the pq to
        ;; A Query or UpdateRequest to extract the prefixes.
        ;; Bonus, this gives validation of sorts at generation time
        ^PrefixMapping prefix-mapping
        (case query-type
          :update (.getPrefixMapping (sparql/as-update syntax statement))
          :query (.getPrefixMapping (sparql/as-query syntax statement)))]
    (fn [connection call-options]
      (sparql-fn connection
                 prefix-mapping
                 syntax
                 (.copy query false)
                 call-options))))

(defn- connection-error
  [name]
  (format
   (join
    "\n"
    ["No database connection supplied to function '%s',"
     "Check the docs, and supply {:connection ...} as an option to the function call, or globally to the declaration."])
   name))


;;; Public API
(defn generate-query-fn
  "Generate a function to run a query
   - if the name ends with `!` a SPARQL UPDATE will be executed
   - otherwise a SPARQL QUERY will be executed. "
  [{:keys [name docstring statement filename]
    :or {filename ""}
    :as query}
   query-options]
  (assert name      "Query name is mandatory.")
  (assert statement "Query statement is mandatory.")
  (let [global-connection
        (:connection query-options)
        handler-fn
        (statement-handler name filename statement)
        real-fn
        (fn [call-options]
          (let [connection (or (:connection call-options) global-connection)]
            (assert connection (connection-error name))
            (handler-fn connection call-options)))
        [display-args generated-fn]
        (let [global-args {:keys ['connection 'bindings]}]
          [(list [] [global-args] [global-args 'kv-bindings*])
           (fn query-wrapper-fn
             ([] (query-wrapper-fn {}))
             ([call-options] (real-fn call-options))
             ([call-options & {:as arg-bindings}]
              (real-fn (update call-options :bindings merge arg-bindings))))])]

    (with-meta generated-fn
      (merge {:name name
              :arglists display-args
              :tag 'java.lang.AutoCloseable
              ::source (str statement)}
             (when docstring
               {:doc docstring})))))


(defn generate-var [this options]
  (create-root-var (:name this)
                   (generate-query-fn this options)))
