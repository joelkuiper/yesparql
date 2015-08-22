(ns yesparql.tdb
  (:import
   [org.apache.jena.query Dataset DatasetFactory]
   [org.apache.jena.query.text TextDatasetFactory]
   [org.apache.jena.tdb TDBFactory TDBLoader StoreConnection TDB]))

(defn ^Dataset create-file-based
  "Creates a new TDB-backed `Dataset` in the `directory` (absolute path)"
  [^String directory]
  (TDBFactory/createDataset directory))

(defn ^Dataset create-assembler-based
  "Creates a new TDB-backed `Dataset` with the provided assembler file (TTL, absolute path)"
  [assembler & {:keys [text-index]}]
  (if text-index
    (TextDatasetFactory/create assembler)
    (TDBFactory/assembleDataset assembler)))

(defn ^Dataset create-in-memory
  "Create an in-memory, modifiable TDB `Dataset`"
  []
  (TDBFactory/createDataset))

(defn ^Dataset create-bare
  "Creates a bare `DataSet` (without TDB)
   Recommended for testing purposes only"
  []
  (DatasetFactory/createMem))
