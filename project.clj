(defproject yesparql "0.3.0"
  :description "YeSPARQL, a Yesql inspired SPARQL library"
  :url "http://github.com/joelkuiper/yesparql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/joelkuiper/yesparql"}
  :profiles {:dev {:dependencies
                   [[expectations "2.1.3"]]
                   :plugins [[michaelblume/lein-marginalia "0.9.0"]
                             [lein-autoexpect "1.4.0"]
                             [lein-expectations "0.0.8"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [instaparse "1.4.1"]
                 [org.apache.jena/jena-arq "3.0.1"]
                 [org.apache.jena/jena-core "3.0.1"]
                 [org.apache.jena/jena-querybuilder "3.0.1"]])
