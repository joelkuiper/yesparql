# YeSPARQL

[![Build Status](https://travis-ci.org/joelkuiper/yesparql.png?branch=master)](https://travis-ci.org/joelkuiper/yesparql)


YeSPARQL is a library for executing [SPARQL](http://www.w3.org/TR/sparql11-query/) queries against endpoints or [TDB stores](https://jena.apache.org/documentation/tdb/index.html), heavily influenced by [Yesql](https://github.com/krisajenkins/yesql).

[Annotated Source](https://joelkuiper.github.io/yesparql/uberdoc.html)


## Installation
[![Clojars Project](http://clojars.org/yesparql/latest-version.svg)](http://clojars.org/yesparql)

Add this to your [Leiningen](https://github.com/technomancy/leiningen) `:dependencies`:

``` clojure
[yesparql "0.3.1"]
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
See my introductory blog post on Semantic Web and SPARQL: [Whatever happened to Semantic Web?](https://joelkuiper.eu/semantic-web) or see [SPARQL by Example](http://www.cambridgesemantics.com/semantic-university/sparql-by-example).

## Usage
### One File, One Query
Create an SPARQL query and save it as a file.

```sparql
-- Example dbpedia query, returning intellectuals restricted by subject
-- Endpoint: http://dbpedia.org/sparql

PREFIX dbpedia-owl: <http://dbpedia.org/ontology/>
PREFIX dct: <http://purl.org/dc/terms/>
PREFIX category: <http://dbpedia.org/resource/Category:>

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
The [syntax](https://jena.apache.org/documentation/javadoc/arq/org/apache/jena/query/Syntax.html) of the query is automatically guessed at using the filename, and currently defaults to [SPARQL 1.1](https://www.w3.org/TR/sparql11-overview/).

```clojure
(require '[yesparql.core :refer [defquery]])


;; Import the SPARQL query as a function.
;; In this case we use DBPedia as a remote endpoint
(defquery select-intellectuals "some/where/query.sparql"
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
;; The result is a lazy sequence of Clojure data structures
(with-open [result (select-intellectuals)]
  (do-something-with-result! result))
```

You can supply bindings as a map of strings (the names) or keywords to [`URI`](https://docs.oracle.com/javase/7/docs/api/java/net/URI.html), `URL`, [`RDFNode`](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/rdf/model/RDFNode.html), [`Node`](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/graph/Node.html), or [Literal](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/rdf/model/Literal.html) (default).
Alternatively, you can supply a map of `{:type (optional, uri), :lang (optional, str or keyword), :value}` which will be coerced to the appropriate `Literal` automatically. Prefixes that were defined in the query get automatically resolved when passing in an `URI` for SPARQL QUERY, only common prefixes are resolved for SPARQL UPDATE.

These bindings get inserted into the query using a [Parameterized SPARQL String](https://jena.apache.org/documentation/query/parameterized-sparql-strings.html).

In addition you can overwrite `limit`, `offset`, fields at query time.

A complete example of running a SPARQL SELECT against [DBPedia](http://wiki.dbpedia.org/), with initial bindings and limit:

```clojure
user> (require '[yesparql.sparql :refer :all])

user> (with-open
        [result
         (select-intellectuals
          {:limit 10
           :bindings
           {:subject
            (java.net.URI. "category:1952_deaths")}})]
        (into [] result))

;=> [{"person" "http://dbpedia.org/resource/Antonio_Damasio"}
;=>  {"person" "http://dbpedia.org/resource/Albert_Victor_B%C3%A4cklund"}
;=>  {"person" "http://dbpedia.org/resource/Alexander_Oparin"}
;=>  {"person" "http://dbpedia.org/resource/Alexander_Stepanovich_Popov"}
;=>  {"person" "http://dbpedia.org/resource/Andrew_Ainslie_Common"}
;=>  {"person" "http://dbpedia.org/resource/Annie_Montague_Alexander"}
;=>  {"person" "http://dbpedia.org/resource/Anthony_James_Leggett"}
;=>  {"person" "http://dbpedia.org/resource/Ascanio_Sobrero"}
;=>  {"person" "http://dbpedia.org/resource/Axel_Thue"}
;=>  {"person" "http://dbpedia.org/resource/B%C3%A9la_Bollob%C3%A1s"}]
```

You can also transform the result directly into other formats like CSV:

```clojure
user> (with-open [result (select-intellectuals)]
        (result->csv (->result result)))

;=> person
;=> http://dbpedia.org/resource/Antonio_Damasio
;=> http://dbpedia.org/resource/Albert_Victor_B%C3%A4cklund
;=> http://dbpedia.org/resource/Alexander_Oparin
;=> http://dbpedia.org/resource/Alexander_Stepanovich_Popov
;=> http://dbpedia.org/resource/Andrew_Ainslie_Common
;=> http://dbpedia.org/resource/Annie_Montague_Alexander
;=> http://dbpedia.org/resource/Anthony_James_Leggett
;=> http://dbpedia.org/resource/Ascanio_Sobrero
;=> http://dbpedia.org/resource/Axel_Thue
;=> http://dbpedia.org/resource/B%C3%A9la_Bollob%C3%A1s
```

**WARNING**: Queries should be called in a `with-open` in order to close the underlying [`QueryExecution`](https://jena.apache.org/documentation/javadoc/arq/), or be closed manually.
The underlying `ResultSet` (and result iterator) will become invalid after closing (see `copy-result-set`).
While it is completely possible to not close the result, it will leak resources and is not advisable.

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

### Result serialization
YeSPARQL offers various functions to serialize `Model` and `ResultSet` in the `yesparql.sparql` namespace.

```clojure
user> (require '[yesparql.sparql :refer :all])

user> (def result
        (with-open [result (select-intellectuals)]
          (copy-result-set (->result result))))

;; Converting results...
user> (result->json result)
user> (result->csv result)
user> (result->xml result) ; NOT RDF, but the SPARQL XML result format
```
You can use `result->model` to convert a `ResultSet` (SELECT) to a `Model`; or use `->model` on the result of CONSTRUCT and DESCRIBE queries.

```clojure
;; Convert to model
user> (def model (result->model result))

;; Then choose one of the serializations...
user> (model->json-ld model)
user> (model->rdf+xml model)
user> (model->ttl model)

;; Or use one of the other serialization formats
user> (serialize-model model "format")
```

See [Jena Model Write formats](https://jena.apache.org/documentation/io/rdf-output.html#jena_model_write_formats) for formats that can be passed to `serialize-model`.

If a `ResultSet` has to be traversed multiple times use the `copy-result-set`, which generates a rewindable copy of the entire `ResultSet` (as in the example above).

See also [`ResultSetFactory/makeRewindable`](https://jena.apache.org/documentation/javadoc/arq/org/apache/jena/query/ResultSetFactory.html#makeRewindable-org.apache.jena.rdf.model.Model-).

## A note on lazyness
The results are returned in a lazy fashion, but the underlying `ResultSet` will become invalid after closing the result. So this won't work:

```clojure
(with-open [r (select-intellectuals)] (map println r))

ARQException ResultSet no longer valid (QueryExecution has been
closed) org.apache.jena.sparql.engine.ResultSetCheckCondition.check
(ResultSetCheckCondition.java:106)
```

Instead do:

```clojure
(with-open [results (select-intellectuals)]
   (doseq [r results]
     (println r)))

;=>{person http://dbpedia.org/resource/Antonio_Damasio}
;=>{person http://dbpedia.org/resource/Albert_Victor_B%C3%A4cklund}
;=>{person http://dbpedia.org/resource/Alexander_Oparin}
;=>{person http://dbpedia.org/resource/Alexander_Stepanovich_Popov}
;=>{person http://dbpedia.org/resource/Andrew_Ainslie_Common}
;=>{person http://dbpedia.org/resource/Annie_Montague_Alexander}
;=>{person http://dbpedia.org/resource/Anthony_James_Leggett}
;=>{person http://dbpedia.org/resource/Ascanio_Sobrero}
;=>{person http://dbpedia.org/resource/Axel_Thue}
;=>{person http://dbpedia.org/resource/B%C3%A9la_Bollob%C3%A1s}
```

In general make sure you do any and all work you want to do on the results *eagerly* in the `with-open`.

# A note on transactions
When executing against TDB it is recommended to use [transactions](https://jena.apache.org/documentation/tdb/tdb_transactions.html)
to prevent against data corruption.
You can set the flags yourself on the `Dataset` or use the `with-transaction` macro, although support for this is somewhat lacking.

## TODO
- Authentication support for SPARQL Endpoints
- Support [SPARQL S-Expressions](https://jena.apache.org/documentation/notes/sse.html) (?)

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

## Funding

This software was commissioned and sponsored by [Doctor Evidence](http://doctorevidence.com/). The Doctor Evidence mission is to improve clinical outcomes by finding and delivering medical evidence to healthcare professionals, medical associations, policy makers and manufacturers through revolutionary solutions that enable anyone to make informed decisions and policies using medical data that is more accessible, relevant and readable.

## License

Copyright © 2015-2018 Joël Kuiper

Distributed under the Eclipse Public License, the same as Clojure.

*Does it SPARQL? :sunny:*
