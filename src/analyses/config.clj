(ns analyses.config
  (:require [clojure-commons.config :as cc]
            [me.raynes.fs :as fs]
            [slingshot.slingshot :refer [throw+]]))

(def docs-uri "/docs")

(def svc-info
  {:desc     "API for the Discovery Environment's analyses."
   :app-name "analyses"
   :group-id "org.cyverse"
   :art-id   "analyses"
   :service  "analyses"})

(def ^:private props
  "A ref for storing the configuration properties."
  (ref nil))

(def ^:private config-valid
  "A ref for storing a configuration validity flag."
  (ref true))

(def ^:private configs
  "A ref for storing the symbols used to get configuration settings."
  (ref []))

(cc/defprop-optint listen-port
  "The port that the analyses service listens on."
  [props config-valid configs]
  "analyses.listen-port" 60000)

(cc/defprop-optstr db-driver-class
  "The name of the JDBC driver to use."
  [props config-valid configs]
  "analyses.db.driver" "org.postgresql.Driver")

(cc/defprop-optstr db-subprotocol
  "The subprotocol to use when connecting to the database (e.g.
   postgresql)."
  [props config-valid configs]
  "analyses.db.subprotocol" "postgresql")

(cc/defprop-optstr db-host
  "The host name or IP address to use when
   connecting to the database."
  [props config-valid configs]
  "analyses.db.host" "dedb")

(cc/defprop-optstr db-port
  "The port number to use when connecting to the database."
  [props config-valid configs]
  "analyses.db.port" "5432")

(cc/defprop-optstr db-name
  "The name of the database to connect to."
  [props config-valid configs]
  "analyses.db.name" "de")

(cc/defprop-optstr db-user
  "The username to use when authenticating to the database."
  [props config-valid configs]
  "analyses.db.user" "de")

(cc/defprop-optstr db-password
  "The password to use when authenticating to the database."
  [props config-valid configs]
  "analyses.db.password" "notprod")

(cc/defprop-optstr data-info-base-uri
  "The base URI for the data-info service"
  [props config-valid configs]
  "analyses.data-info.base-uri" "http://data-info")

(cc/defprop-optstr apps-base-uri
  "The base URI for the apps service"
  [props config-valid configs]
  "analyses.apps.base-uri" "http://apps")

(cc/defprop-str username-suffix
  "The suffix appended to fully qualified usernames."
  [props config-valid configs]
  "analyses.username.suffix")

(defn- validate-config
  "Validates the configuration settings after they've been loaded."
  []
  (when-not (cc/validate-config configs config-valid)
    (throw+ {:type :clojure-commons.exception/invalid-configuration})))

(defn- load-and-validate-config-file
  "Loads the configuration settings from a file."
  [cfg-path & [{:keys [log-config?] :or {log-config? true}}]]
  (cc/load-config-from-file cfg-path props)
  (when log-config?
    (cc/log-config props))
  (validate-config))

(defn- iplant-conf-dir-file
  [filename]
  (when-let [conf-dir (System/getenv "IPLANT_CONF_DIR")]
    (let [f (fs/file conf-dir filename)]
      (when (.isFile f) (.getPath f)))))

(defn- cwd-file
  [filename]
  (let [f (fs/file filename)]
    (when (.isFile f) (.getPath f))))

(defn- no-configuration-found
  [filename]
  (throw (RuntimeException. (str "configuration file " filename " not found"))))

(defn- find-config-file
  []
  (let [conf-file "analyses.properties"]
    (or (iplant-conf-dir-file conf-file)
        (cwd-file conf-file)
        (no-configuration-found conf-file))))

(defn load-config-from-file
  "Loads the configuration settings from a properties file."
  [& [cfg-path]]
  (load-and-validate-config-file (or cfg-path (find-config-file))))
