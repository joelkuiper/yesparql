(ns yesparql.generate
  (:require
   [clojure.set :as set]
   [clojure.string :refer [join lower-case]]
   [yesparql.util :refer [create-root-var]]
   [yesparql.sparql :as sparql]
   [yesparql.types :refer [map->Query]])
  (:import [yesparql.types Query]
           [org.apache.jena.query ParameterizedSparqlString]))

(defn statement-handler
  [^String name ^ParameterizedSparqlString query]
  (let [sparql-fn
        (cond
          (= (last name) \!) sparql/update!
          :else sparql/query)]
    (fn [connection query call-options]
      (sparql-fn connection query call-options))))

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
   - otherwise a SPARQL QUERY will be executed.

   [FOR TESTING] you can override this behavior by passing a `:query-fn` at call or query time.
   `query-fn` is a function with the signature `[data-set pq call-options & args]` and will be used instead."
  [{:keys [name docstring statement]
    :as query}
   query-options]
  (assert name      "Query name is mandatory.")
  (assert statement "Query statement is mandatory.")
  (let [global-connection (:connection query-options)
        query (sparql/parameterized-query statement)
        default-handler (or (:query-fn query-options) (statement-handler name query))
        real-fn
        (fn [call-options]
          (let [handler-fn (or (:query-fn call-options) default-handler)
                connection (or (:connection call-options) global-connection)]
            (assert connection (connection-error name))
            (handler-fn connection (.copy query false) call-options)))
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
