# YeSPARQL

YeSPARQL is a library for executing [SPARQL](http://www.w3.org/TR/sparql11-query/) queries against endpoints or [TDB stores](https://jena.apache.org/documentation/tdb/index.html), heavily influenced by [Yesql](https://github.com/krisajenkins/yesql).


## Installation
Add this to your [Leiningen](https://github.com/technomancy/leiningen) `:dependencies`:

``` clojure
[yesparql "0.1.4"]
```

## What's the point?
[Yesql](https://github.com/krisajenkins/yesql) does a much better job explaining this. But in short, it's annoying to write SPARQL in Clojure.
While you could design some DSL, these are often lacking in expressiveness and have nasty corner cases; so why not *just use SPARQL*.
By defining the queries as simple SPARQL in separate files, you get a clean separation of concerns without polluting your code with long queries.
Other perks include:

- Better editor support.
- Team interoperability. Your DBAs can read and write the SPARQL you
  use in your Clojure project.
- Easier performance tuning. It's much easier when your query is ordinary SPARQL.
- Query reuse. Drop the same SPARQL files into other projects, because
  they're just plain ol' SPARQL. Share them as a submodule.


## Eeh, I meant what's the point of SPARQL?
See my introductory blog post on Semantic Web and SPARQL: [Whatever happened to Semantic Web?](https://joelkuiper.eu/semantic-web)

## Usage
### One File, One Query
Create an SPARQL query.

```sparql
-- Example dbpedia query, returning intellectuals restricted by subject
-- Endpoint: http://dbpedia.org/sparql

PREFIX dbpedia-owl: <http://dbpedia.org/ontology/>
PREFIX dct: <http://purl.org/dc/terms/>

SELECT DISTINCT ?person {
    { ?person a dbpedia-owl:Scientist }
    UNION
    { ?person a dbpedia-owl:Writer }
    UNION
    { ?person a dbpedia-owl:Philosopher }
    ?person dct:subject ?subject .
} LIMIT 10
```

Make sure it's on the classpath. For this example, it's in `src/some/where/`.

```clojure
(require '[yesparql.core :refer [defquery]])

;; Import the SPARQL query as a function.
;; In this case we use DBPedia as a remote endpoint
(defquery select-intellectuals "some/where/select-intellectuals.sql"
  {:connection "http://dbpedia.org/sparql"})

;; The function is now available in the namespace
;; Docstrings are automatically generated
(clojure.repl/doc select-intellectuals)

;=> ------------------------
;=> yesparql.core-test/select-intellectuals
;=> ([{:keys [connection bindings]}])
;=> Example dbpedia query, returning intellectuals restricted by subject
;=> Endpoint: http://dbpedia.org/sparql

;; Running the query is as easy as calling the function
(select-intellectuals)
```

In addition, you can supply bindings as a map of strings (the names) to [`URI`](https://docs.oracle.com/javase/7/docs/api/java/net/URI.html), `URL`, [`RDFNode`](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/rdf/model/RDFNode.html), [`Node`](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/graph/Node.html), or literals (default).
A complete example of running a SPARQL SELECT against DBPedia, with initial bindings:

```clojure
(print
 (sparql/result->csv
  (select-intellectuals
   {:bindings
    {"subject" (URI. "http://dbpedia.org/resource/Category:1952_deaths")}})))

;=> person
;=> http://dbpedia.org/resource/Bernard_Lyot
;=> http://dbpedia.org/resource/Henry_Drysdale_Dakin
;=> http://dbpedia.org/resource/Felix_Ehrenhaft
;=> http://dbpedia.org/resource/T._Wayland_Vaughan
;=> http://dbpedia.org/resource/Luigi_Puccianti
;=> http://dbpedia.org/resource/Max_Dehn
;=> http://dbpedia.org/resource/James_Irvine_(chemist)
;=> http://dbpedia.org/resource/Morris_E._Leeds
;=> http://dbpedia.org/resource/Walter_Tennyson_Swingle
;=> http://dbpedia.org/resource/Andrew_Lawson
```

### TDB
In addition to supplying a SPARQL Endpoint URL, you can also supply a TDB `Dataset`.
The `yesparql.tdb` namespace provides convenience methods for constructing these.

```clojure
(require '[yesparql.tdb :as tdb])

(def tdb (tdb/create-in-memory))

(defquery select-all
  "yesparql/samples/select.sparql"
  {:connection tdb})

```

### One file, Many Queries
[Same as Yesql](https://github.com/krisajenkins/yesql#one-file-many-queries)

### Query types
Since SPARQL has multiple query types we consider the following syntax for the query names:

- Names starting with `select` (e.g. `select-intellectuals`) perform a [SPARQL SELECT](http://www.w3.org/TR/rdf-sparql-query/#select)
- Names starting with `update` or ending with `!`  perform a [SPARQL UPDATE](http://www.w3.org/TR/sparql11-update/)
- Names starting with `ask` or ending with `?`  perform a [SPARQL ASK](http://www.w3.org/TR/rdf-sparql-query/#ask)
- Names starting with `construct` perform a [SPARQL CONSTRUCT](http://www.w3.org/TR/rdf-sparql-query/#construct)
- Names starting with `describe` perform a [SPARQL DESCRIBE](http://www.w3.org/TR/rdf-sparql-query/#describe)

### Results processing
Each of the executed queries returns its native [Apace Jena](https://jena.apache.org/) [ResultSet](https://jena.apache.org/documentation/javadoc/arq/) or [Model](https://jena.apache.org/documentation/javadoc/jena/) (depending on the type of query).

YeSPARQL offers various functions to transform these types to other serializations in the `yesparql.sparql` namespace.

```clojure

(require '[yesparql.sparql :as sparql])

(def result (select-intellectuals))

(sparql/result->clj result) ; converts to a Clojure map using the JSON serialization
(sparql/result->json result)
(sparql/result->csv result)
(sparql/result->xml result) ; NOT RDF, but the SPARQL RDF result format

;; Only a Model can converted to RDF serializations.
;; You can use rdf->model to convert a ResultSet to a Model.
;; CONSTRUCT returns a Model, and does not need to be converted
;; ASK returns a boolean, as expected

(def model (sparql/result->model result))
(sparql/model->json-ld model)
(sparql/model->rdf+xml model)
(sparql/model->ttl model)

(serialize-model model format)
```
See [Jena Model Write formats](https://jena.apache.org/documentation/io/rdf-output.html#jena_model_write_formats) for additional formats that can be passed to `serialize-model`.

Note that it is not a primary goal to provide a full native Clojure wrapper.
It's perfectly to fine to keep using the Jena objects, and use the Clojure-Java [interop](http://clojure.org/java_interop).

## TODO
- TDB Text API (with Lucene)
- Better support for various binding types (prefixes, RDFNode, etc)
- Authentication support for SPARQL Endpoints
- Support [SPARQL S-Expressions](https://jena.apache.org/documentation/notes/sse.html) (?)
- More tests
- Better docstrings

## New to Clojure?
If you are new to Clojure the code might look unfamiliar.
But, Clojure is a wonderful language, and if you are interested in learning we recommend the following resources:

- [Clojure Distilled](https://yogthos.github.io/ClojureDistilled.html)
- [Clojure for the brave and true](http://www.braveclojure.com/)
- [Clojure from the ground up](https://aphyr.com/tags/Clojure-from-the-ground-up)
- [The Joy of Clojure](http://www.amazon.com/Joy-Clojure-Michael-Fogus/dp/1617291412)

## Acknowledgments
[Kris Jenkins](https://github.com/krisajenkins) for providing much of the idea and initial code

## License

Copyright © 2015 Joel Kuiper

Distributed under the Eclipse Public License, the same as Clojure.
