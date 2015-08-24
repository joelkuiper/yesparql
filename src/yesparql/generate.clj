(ns yesparql.generate
  (:require
   [clojure.set :as set]
   [clojure.string :refer [join lower-case]]
   [yesparql.util :refer [create-root-var]]
   [yesparql.sparql :as sparql]
   [yesparql.types :refer [map->Query]])
  (:import [yesparql.types Query]))

(defn statement-handler
  "Parses the name of a query and returns the appropriate handler"
  [name]
  (let [sparql-fn
        (cond
          (re-matches #"select-.+" name) sparql/select
          (or (re-matches #"\w+\?" name) (re-matches #"ask-\w+" name)) sparql/ask
          (or (re-matches #"\w+\!" name) (re-matches #"update-\w+" name)) sparql/update
          (re-matches #"describe-.+" name) sparql/describe
          (re-matches #"construct-.+" name) sparql/construct
          :else sparql/select)]
    (fn [connection statement call-options]
      (sparql-fn connection statement call-options))))

(defn- connection-error
  [name]
  (format
   (join "\n"
         ["No database connection supplied to function '%s',"
          "Check the docs, and supply {:connection ...} as an option to the function call, or globally to the defquery declaration."])
   name))

(defn generate-query-fn
  "Generate a function to run a query
   - If the name starts with `select-` a SPARQL query will be executed
   - If the name starts with `describe-` a SPARQL describe will be executed
   - If the name starts with `update-` or ends with `!` a SPARQL update will be executed
   - If the name starts with `ask-` or ends with `?` a SPARQL ask will be executed
   - If the name starts with `construct-` a SPARQL construct will be executed
   - otherwise a SPARQL select will be executed.
   You can override this behavior by passing a `:query-fn` at call or query time.
   `query-fn` is a function with the signature `[data-set statement call-options]`."
  [{:keys [name docstring statement]
    :as query}
   query-options]
  (assert name      "Query name is mandatory.")
  (assert statement "Query statement is mandatory.")
  (let [global-connection (:connection query-options)
        default-handler (or (:query-fn query-options) (statement-handler name))
        real-fn
        (fn [call-options]
          (let [handler-fn (or (:query-fn call-options) default-handler)
                connection (or (:connection call-options) global-connection)]
            (assert connection (connection-error name))
            (handler-fn connection statement call-options)))
        [display-args generated-fn]
        (let [global-args {:keys ['connection 'bindings]}]
          [(list [] [global-args])
           (fn query-wrapper-fn
             ([] (query-wrapper-fn {}))
             ([call-options] (real-fn call-options)))])]

    (with-meta generated-fn
      (merge {:name name
              :arglists display-args
              ::source (str statement)}
             (when docstring
               {:doc docstring})))))


(defn generate-var [this options]
  (create-root-var (:name this)
                   (generate-query-fn this options)))
