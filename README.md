# YeSPARQL

YESPARQL is a library for executing [SPARQL](http://www.w3.org/TR/sparql11-query/) queries against endpoints or [TDB stores](https://jena.apache.org/documentation/tdb/index.html), heavily influenced by [YESQL](https://github.com/krisajenkins/yesql).


## Installation
Add this to your [Leiningen](https://github.com/technomancy/leiningen) `:dependencies`:

``` clojure
[yesparql "0.1.0"]
```

## What's the point?
[YESQL](https://github.com/krisajenkins/yesql) does a much better job explaining this. But in short, it's annoying to write SPARQL in Clojure.
While you could design some DSL, these are often lacking in expressiveness and have nasty corner cases; so why not *just use SPARQL*.
By defining the queries as simple SPARQL in separate files, you get a clean separation of concerns without polluting your code with long queries.
Other perks include:

- Better editor support.
- Team interoperability. Your DBAs can read and write the SPARQL you
  use in your Clojure project.
- Easier performance tuning. It's much easier when your query is ordinary SQL.
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


; Import the SQL query as a function. In this case we use DBPedia as a remote endpoint
(defquery select-intellectuals "some/where/dbpedia-select.sql"
  {:connection "http://dbpedia.org/sparql"})
```

```clojure
(clojure.repl/doc select-intelectuals)

;=> ------------------------
;=> yesparql.core-test/dbpedia-select
;=> ([] [{:keys [connection]}])
;=> Example dbpedia query, returning intellectuals restricted by subject
;=> Endpoint: http://dbpedia.org/sparql

```

You can supply default(/initial) bindings as a map of strings (the names) to `URI`, `URL`, `RDFNode`, `Node`, or literals (default).

```clojure
(select-intellectuals
 {:bindings
  {"subject" (URI. "http://dbpedia.org/resource/Category:1942_deaths")}})
```

In addition to supplying a SPARQL Endpoint URL, you can also supply a TDB `Dataset`.
The `yesparq.tdb` namespace provides convenience methods for constructing these.

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

- Names starting with `select` (e.g. select-intellectuals) perform a [SPARQL SELECT](http://www.w3.org/TR/rdf-sparql-query/#select)
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
(sparql/result->xml result) ; NOT RDF, but the SPARQL RDF Result format

;; Only models can converted to RDF serializations,
;; You can use rdf->model to convert a ResultSet

;; CONSTRUCT queries will return a model natively, so no conversion is required.

;; ASK returns a boolean, as expected

(def model (sparql/result->model result))
(sparql/model->json-ld result)
(sparql/model->rdf+xml result)
(sparql/model->ttl result)
```


## TODO
- TDB Text API (with Lucene)
- Better support for various binding types (prefixes, RDFNode, etc)
- More tests
- Better docstrings

## Acknowledgments
[Kris Jenkins](https://github.com/krisajenkins) for providing much of the idea and initial code

## License

Copyright © 2015 Joel Kuiper

Distributed under the Eclipse Public License, the same as Clojure.
