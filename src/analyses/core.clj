(ns analyses.core
  (:gen-class)
  (:require
   [analyses.routes :as routes]
   [analyses.config :as config]
   [analyses.persistence.common :as common-persist]
   [clj-http.client :as http]
   [clojure.tools.logging :as log]
   [common-cli.core :as ccli]
   [me.raynes.fs :as fs]
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
  ((eval 'ring.adapter.jetty/run-jetty) routes/app {:port (config/listen-port) :join false}))

(defn init [& [options]]
  (config/load-config-from-file (:config options))
  (common-persist/define-database))

(defn -main
  [& args]
  (tc/with-logging-context config/svc-info
    (let [{:keys [options _arguments _errors _summary]} (ccli/handle-args config/svc-info args cli-options)]
      (when-not (fs/exists? (:config options))
        (ccli/exit 1 "The config file does not exist."))
      (when-not (fs/readable? (:config options))
        (ccli/exit 1 "The config file is not readable."))
      (init options)
      (http/with-connection-pool {:timeout 5 :threads 10 :insecure? false :default-per-route 10}
        (run-jetty)))))
