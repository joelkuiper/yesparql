# YeSPARQL

[![Build Status](https://travis-ci.org/joelkuiper/yesparql.png?branch=master)](https://travis-ci.org/joelkuiper/yesparql)


YeSPARQL is a library for executing [SPARQL](http://www.w3.org/TR/sparql11-query/) queries against endpoints or [TDB stores](https://jena.apache.org/documentation/tdb/index.html), heavily influenced by [Yesql](https://github.com/krisajenkins/yesql).


## Installation
[![Clojars Project](http://clojars.org/yesparql/latest-version.svg)](http://clojars.org/yesparql)

Add this to your [Leiningen](https://github.com/technomancy/leiningen) `:dependencies`:

``` clojure
[yesparql "0.1.5"]
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
;=> ([] [{:keys [connection bindings]}])
;=> Example dbpedia query, returning intellectuals restricted by subject
;=> Endpoint: http://dbpedia.org/sparql

;; Running the query is as easy as calling the function in a with-open
(with-open [result (select-intellectuals)]
  (do-something-with-result!))
```

In addition, you can supply bindings as a map of strings (the names) or integers (positional arguments) to [`URI`](https://docs.oracle.com/javase/7/docs/api/java/net/URI.html), `URL`, [`RDFNode`](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/rdf/model/RDFNode.html), [`Node`](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/graph/Node.html), or Literals (default).
These bindings get inserted into the query using a [Parameterized SPARQL String](https://jena.apache.org/documentation/query/parameterized-sparql-strings.html).
A complete example of running a SPARQL SELECT against DBPedia, with initial bindings:

```clojure
(map println
  (select-intellectuals
   {:bindings
    {"subject" (java.net.URI. "http://dbpedia.org/resource/Category:1952_deaths")}}))

;=> {person http://dbpedia.org/resource/Bernard_Lyot}
;=> {person http://dbpedia.org/resource/Henry_Drysdale_Dakin}
;=> {person http://dbpedia.org/resource/Felix_Ehrenhaft}
;=> {person http://dbpedia.org/resource/T._Wayland_Vaughan}
;=> {person http://dbpedia.org/resource/Luigi_Puccianti}
;=> {person http://dbpedia.org/resource/Max_Dehn}
;=> {person http://dbpedia.org/resource/James_Irvine_(chemist)}
;=> {person http://dbpedia.org/resource/Morris_E._Leeds}
;=> {person http://dbpedia.org/resource/Walter_Tennyson_Swingle}
;=> {person http://dbpedia.org/resource/Andrew_Lawson}
```

### One file, Many Queries
[Same as Yesql](https://github.com/krisajenkins/yesql#one-file-many-queries)

### [SPARQL Injection Notes](https://jena.apache.org/documentation/javadoc/arq/org/apache/jena/query/ParameterizedSparqlString.html)

While `ParameterizedSparqlString` was in part designed to prevent SPARQL injection it is by no means foolproof because it works purely at the textual level. The current version of the code addresses some possible attack vectors that the developers have identified but we do not claim to be sufficiently devious to have thought of and prevented every possible attack vector.

Therefore we strongly recommend that users concerned about SPARQL Injection attacks perform their own validation on provided parameters and test their use of this class themselves prior to its use in any security conscious deployment. We also recommend that users do not use easily guess-able variable names for their parameters as these can allow a chained injection attack, though generally speaking the code should prevent these.

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

### Query types
Since SPARQL has multiple query types we consider the following syntax for the query names:

- Names ending with `!` will perform a [SPARQL UPDATE](http://www.w3.org/TR/sparql11-update/)
- All others will execute a [SPARQL QUERY](http://www.w3.org/TR/sparql11-query/) of types [ASK](http://www.w3.org/TR/rdf-sparql-query/#ask), [SELECT](http://www.w3.org/TR/rdf-sparql-query/#select), [CONSTRUCT](http://www.w3.org/TR/rdf-sparql-query/#construct), or [DESCRIBE](http://www.w3.org/TR/rdf-sparql-query/#describe) depending on the query.

### Result format
Each of the executed queries returns an `iterator-seq` of result binding maps (SELECT), or triples (DESCRIBE, CONSTRUCT) in a native Clojure data structure. ASK simply returns a boolean.

Access to the underlying Jena [`ResultSet`](https://jena.apache.org/documentation/javadoc/arq/org/apache/jena/query/ResultSet.html) and [`QueryExecution`](https://jena.apache.org/documentation/javadoc/arq/org/apache/jena/query/QueryExecution.html) are provided by `->result`, `->query-execution` functions for SELECT queries.

For DESCRIBE and CONSTRUCT access to the Jena iterator of [`Triple`](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/graph/Triple.html)s is provided by `->triples`, in addition to `->query-execution`. A convenience method `->model` can be used to transform the triples in to a Jena [`Model`](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/rdf/model/Model.html).
For example: `(model->json-ld (->model (construct-query))`.

Note that it's perfectly to fine to use these Jena objects, with the Clojure-Java [interop](http://clojure.org/java_interop).

 **WARNING**: Queries should be called in a `with-open` in order to close the underlying [`QueryExecution`](https://jena.apache.org/documentation/javadoc/arq/) or be closed manually.
The underlying `ResultSet` (and result iterator) will become invalid after closing (see `copy-result-set`).
While it is completely possible to not close the result, it will leak resources and is not advisable.

### Result serialization
YeSPARQL offers various functions to serialize `Model` and `ResultSet` in the `yesparql.sparql` namespace.

```clojure

(require '[yesparql.sparql :refer :all])

(def result
  (with-open [result (select-intellectuals)]
    (copy-result-set (->result result))))

(result->clj result) ; converts to a Clojure map using the JSON serialization
(result->json result)
(result->csv result)
(result->xml result) ; NOT RDF, but the SPARQL RDF result format

;; You can use `result->model` to convert a `ResultSet` (SELECT) to a `Model`.
;; Or use `->model` on the result of CONSTRUCT and DESCRIBE queries.

(def model (result->model result))
(model->json-ld model)
(model->rdf+xml model)
(model->ttl model)

(serialize-model model "format")
```
See [Jena Model Write formats](https://jena.apache.org/documentation/io/rdf-output.html#jena_model_write_formats) for additional formats that can be passed to `serialize-model`.

If a `ResultSet` has to be traversed multiple times use the `copy-result-set`, which generates a rewindable copy of the entire `ResultSet` (as in the example above).

See also [`ResultSetFactory/makeRewindable`](https://jena.apache.org/documentation/javadoc/arq/org/apache/jena/query/ResultSetFactory.html#makeRewindable-org.apache.jena.rdf.model.Model-).


## TODO
- TDB Text API (with Lucene)
- Support for LIMIT arguments and other non-valid SPARQL bindings
- Authentication support for SPARQL Endpoints
- Support [SPARQL S-Expressions](https://jena.apache.org/documentation/notes/sse.html) (?)
- More tests
- Better docstrings

## New to Clojure?
If you are new to Clojure the code might look unfamiliar.
But, Clojure is a wonderful language, and if you are interested in learning we recommend the following resources:

- [Parens of the Dead](http://www.parens-of-the-dead.com/)
- [Clojure Distilled](https://yogthos.github.io/ClojureDistilled.html)
- [Clojure for the brave and true](http://www.braveclojure.com/)
- [Clojure from the ground up](https://aphyr.com/tags/Clojure-from-the-ground-up)
- [The Joy of Clojure](http://www.amazon.com/Joy-Clojure-Michael-Fogus/dp/1617291412)

## Acknowledgments
- [Kris Jenkins](https://github.com/krisajenkins) for providing much of the idea and initial code
- [Jozef Wagner](https://github.com/wagjo)
- [Rick Moynihan](https://github.com/RickMoynihan)

## License

Copyright © 2015 Joel Kuiper

Distributed under the Eclipse Public License, the same as Clojure.

*Does it SPARQL? :sunny:*
