(ns yesparql.jena
  (:import
   [org.apache.jena.query Dataset DatasetFactory]
   [org.apache.jena.tdb TDBFactory TDBLoader StoreConnection TDB]))

(defn ^Dataset create-file-tdb
  "Creates a new TDB-backed `Dataset` in the `directory` (absolute path)"
  [^String directory]
  (TDBFactory/createDataset directory))

(defn ^Dataset create-assembled-tdb
  "Creates a new TDB-backed `Dataset` with the provided assembler file (TTL, absolute path)"
  [assembler]
  (TDBFactory/assembleDataset assembler))

(defn ^Dataset create-in-mem-tdb
  "Create an in-memory, modifiable `Dataset`"
  []
  (TDBFactory/createDataset))
