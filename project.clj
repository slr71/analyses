(defproject analyses "0.1.0-SNAPSHOT"
            :description "Backend service providing API access to DE analyses."
            :url "https://github.com/cyverse-de/analyses"
            :license {:name "BSD 3-Clause"
                      :url "https://github.com/cyverse-de/analyses/blob/master/LICENSE"}
            :dependencies [[org.clojure/clojure "1.10.0"]
                           [proto-repl "0.3.1"]]
            :repl-options {:init-ns analyses.core})
