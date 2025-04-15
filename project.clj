(defproject analyses "3.0.1-SNAPSHOT"
  :description "Backend service providing API access to DE analyses."
  :url "https://github.com/cyverse-de/analyses"
  :license {:name "BSD 3-Clause"
            :url  "https://github.com/cyverse-de/analyses/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [clj-http "3.13.0"]
                 [lambdaisland/uri "1.19.155"]
                 [medley "1.4.0"]
                 [compojure "1.7.1"]
                 [com.github.seancorfield/honeysql "2.7.1295"]
                 [org.postgresql/postgresql "42.7.5"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.cyverse/clojure-commons "3.0.11"]
                 [org.cyverse/common-cli "2.8.2"]
                 [org.cyverse/common-cfg "2.8.3"]
                 [org.cyverse/common-swagger-api "3.4.10"]
                 [org.flatland/ordered "1.15.12"]
                 [org.cyverse/service-logging "2.8.4"]
                 [me.raynes/fs "1.4.6"]
                 [metosin/reitit "0.8.0"]
                 [ring "1.14.1"]
                 [cheshire "5.13.0"]
                 [slingshot "0.12.2"]]
  :eastwood {:exclude-namespaces [apps.protocols clojure.tools.analyzer.utils :test-paths]
             :linters            [:wrong-arity :wrong-ns-form :wrong-pre-post :wrong-tag :misplaced-docstrings]}
  :plugins [[com.github.clj-kondo/lein-clj-kondo "2025.02.20"]
            [jonase/eastwood "1.4.3"]
            [lein-ancient "0.7.0"]
            [test2junit "1.4.4"]]
  :profiles {:dev     {:dependencies   [[ring "1.14.1"]]
                       :plugins        [[lein-ring "0.12.6"]]
                       :resource-paths ["conf/test"]}
             :repl    {:source-paths ["repl"]}
             :uberjar {:aot          :all
                       :uberjar-name "analyses-standalone.jar"}}
  :main ^:skip-aot analyses.core
  :ring {:handler analyses.routes/app
         :init    analyses.core/init
         :port    31327}
  :repl-options {:init-ns analyses.core}
  :jvm-opts ["-Dlogback.configurationFile=/etc/iplant/de/logging/analyses-logging.xml"])
