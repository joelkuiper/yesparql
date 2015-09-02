(ns yesparql.core
  (:require [yesparql.util :refer [slurp-from-classpath]]
            [yesparql.generate :refer [generate-var]]
            [yesparql.queryfile-parser :refer [parse-tagged-queries]]))

;; Most of the non-SPARQL code is directly from [Yesql](https://github.com/krisajenkins/yesql/blob/devel/src/yesql/core.clj) by Kris Jenkins

(defn defqueries
  "Defines several query functions, as defined in the given SPARQL file.
  Each query in the file must begin with a `-- name: <function-name>` marker,
  followed by optional comment lines (which form the docstring), followed by
  the query itself."
  ([filename]
   (defqueries filename {}))
  ([filename options]
   (doall (->> filename
             slurp-from-classpath
             parse-tagged-queries
             (map #(generate-var % options))))))

(defn defquery*
  [name filename options]
  ;;; TODO Now that we have a better parser, this is a somewhat suspicious way of writing this code.
  (doall (->> filename
            slurp-from-classpath
            (format "-- name: %s\n%s" name)
            parse-tagged-queries
            (map #(generate-var % options)))))

;;; defquery is a macro solely because of the unquoted symbol it accepts
;;; as its first argument. It is tempting to deprecate defquery. There
;;; again, it makes things so easy to get started with yesql it might
;;; be worth keeping for that reason alone.
(defmacro defquery
  "Defines a query function, as defined in the given SPARQL file.
  Any comments in that file will form the docstring."
  ([name filename]
   `(defquery ~name ~filename {}))
  ([name filename options]
   `(defquery* ~(str name) ~filename ~options)))
