(defproject analyses "3.0.6-SNAPSHOT"
  :description "Backend service providing API access to DE analyses."
  :url "https://github.com/cyverse-de/analyses"
  :license {:name "BSD 3-Clause"
            :url  "https://github.com/cyverse-de/analyses/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [clj-http "3.13.0"]
                 [lambdaisland/uri "1.19.155"]
                 [medley "1.4.0"]
                 [compojure "1.7.1"]
                 [com.github.seancorfield/honeysql "2.6.1147"]
                 [org.postgresql/postgresql "42.7.3"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.cyverse/clojure-commons "3.0.8"]
                 [org.cyverse/common-cli "2.8.2"]
                 [org.cyverse/common-cfg "2.8.3"]
                 [org.cyverse/common-swagger-api "3.4.5-SNAPSHOT"]
                 [org.flatland/ordered "1.15.12"]
                 [org.cyverse/service-logging "2.8.4"]
                 [me.raynes/fs "1.4.6"]
                 [ring "1.12.2"]
                 [cheshire "5.13.0"]
                 [slingshot "0.12.2"]
                 [proto-repl "0.3.1"]]
  :eastwood {:exclude-namespaces [apps.protocols :test-paths]
             :linters            [:wrong-arity :wrong-ns-form :wrong-pre-post :wrong-tag :misplaced-docstrings]}
  :plugins [[lein-ancient "0.7.0"]
            [test2junit "1.4.4"]
            [jonase/eastwood "1.4.3"]]
  :profiles {:dev {:dependencies   [[ring "1.12.2"]]
                   :plugins        [[lein-ring "0.12.6"]]
                   :resource-paths ["conf/test"]
                   :jvm-opts       ["-Dotel.javaagent.enabled=false"]}
             :repl {:source-paths ["repl"]
                    :jvm-opts     ["-Dotel.javaagent.enabled=false"]}
             :uberjar {:aot :all}}
  :main ^:skip-aot analyses.core
  :ring {:handler analyses.routes/app
         :init analyses.core/init
         :port 31327}
  :repl-options {:init-ns analyses.core}
  :jvm-opts ["-Dlogback.configurationFile=/etc/iplant/de/logging/analyses-logging.xml" "-javaagent:./opentelemetry-javaagent.jar" "-Dotel.resource.attributes=service.name=analyses"])
