(ns yesparql.queryfile-parser
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join trim]]
            [instaparse.core :as instaparse]
            [yesparql.types :refer [map->Query]]
            [yesparql.util :refer [str-non-nil]]
            [yesparql.instaparse-util :refer [process-instaparse-result]]))

(def parser
  (let [url (io/resource "yesparql/queryfile.bnf")]
    (assert url)
    (instaparse/parser url)))

(def parser-transforms
  {:whitespace str-non-nil
   :non-whitespace str-non-nil
   :newline str-non-nil
   :any str-non-nil
   :line str-non-nil
   :comment (fn [& args]
              [:comment (apply str-non-nil args)])
   :docstring (fn [& comments]
                [:docstring (trim (join (map second comments)))])
   :statement (fn [& lines]
                [:statement (trim (join lines))])
   :query (fn [& args]
            (map->Query (into {} args)))
   :queries list})

(defn parse-tagged-queries
  "Parses a string with Yesparql's defqueries syntax into a sequence of maps."
  [text]
  (process-instaparse-result
   (instaparse/transform parser-transforms
                         (instaparse/parses parser
                                            (str text "\n") ;;; TODO This is a workaround for files with no end-of-line marker.
                                            :start :queries))
   {}))
