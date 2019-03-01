(ns analyses.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [common-cli.core :as ccli]
            [clj-http.client :as http]
            [me.raynes.fs :as fs]
            [analyses.routes :as routes]
            [analyses.config :as config]
            [analyses.persistence :as persist]
            [service-logging.thread-context :as tc]))

(defn cli-options
  []
  [["-c" "--config PATH" "Path to the config file"
    :default "/etc/iplant/de/analyses.properties"]
   ["-v" "--version" "Print out the version number."]
   ["-h" "--help"]])

(defn run-jetty
  []
  (require 'ring.adapter.jetty)
  (log/warn "Started listening on" (config/listen-port))
  ((eval 'ring.adapter.jetty/run-jetty) routes/badge-routes {:port (config/listen-port)}))

(defn -main
  [& args]
  (tc/with-logging-context config/svc-info
    (let [{:keys [options arguments errors summary]} (ccli/handle-args config/svc-info args cli-options)]
      (when-not (fs/exists? (:config options))
        (ccli/exit 1 (str "The config file does not exist.")))
      (when-not (fs/readable? (:config options))
        (ccli/exit 1 "The config file is not readable."))
      (config/load-config-from-file (:config options))
      (persist/define-database)
      (http/with-connection-pool {:timeout 5 :threads 10 :insecure? false :default-per-route 10}
        (run-jetty)))))
