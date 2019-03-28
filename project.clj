(defproject analyses "0.1.0-SNAPSHOT"
  :description "Backend service providing API access to DE analyses."
  :url "https://github.com/cyverse-de/analyses"
  :license {:name "BSD 3-Clause"
            :url "https://github.com/cyverse-de/analyses/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-http "3.9.1"]
                 [lambdaisland/uri "1.1.0"]
                 [medley "1.1.0"]
                 [compojure "1.6.1"]
                 [honeysql "0.9.4"]
                 [org.postgresql/postgresql "42.2.5"]
                 [org.clojure/java.jdbc "0.7.9"]
                 [org.cyverse/clojure-commons "3.0.3-SNAPSHOT"]
                 [org.cyverse/common-cli "2.8.1"]
                 [org.cyverse/common-cfg "2.8.1"]
                 [org.cyverse/common-swagger-api "2.10.3-SNAPSHOT"]
                 [org.flatland/ordered "1.5.7"]
                 [org.cyverse/service-logging "2.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [ring "1.7.1"]
                 [cheshire "5.8.1"]
                 [proto-repl "0.3.1"]]
  :eastwood {:exclude-namespaces [apps.protocols :test-paths]
             :linters [:wrong-arity :wrong-ns-form :wrong-pre-post :wrong-tag :misplaced-docstrings]}
  :plugins [[lein-swank "1.4.4"]
            [test2junit "1.1.3"]
            [jonase/eastwood "0.2.6"]]
  :profiles {:dev {:dependencies   [[ring "1.7.1"]]
                   :plugins        [[lein-ring "0.12.5"]]
                   :resource-paths ["conf/test"]}
             :repl {:source-paths ["repl"]}
             :uberjar {:aot :all}}
  :main ^:skip-aot analyses.core
  :ring {:handler analyses.routes/app
         :init analyses.core/load-config-from-file
         :port 31327}
  :repl-options {:init-ns analyses.core})
