(ns analyses.persistence.common
  (:require [analyses.config :as config]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [honey.sql :as sql]))

(defn- create-db-spec
  "Creates the database connection spec to use when accessing the database."
  []
  {:dbtype   (config/db-subprotocol)
   :dbname   (config/db-name)
   :host     (config/db-host)
   :port     (config/db-port)
   :ssl      false
   :user     (config/db-user)
   :password (config/db-password)})

(def de (ref nil))

(defn define-database
  "Defines the database connection to use from within Clojure."
  []
  (dosync (ref-set de (create-db-spec))))

(defn query
  [sqlmap]
  (jdbc/query (deref de) (sql/format sqlmap)))

(defn exec
  [sqlmap]
  (jdbc/with-db-transaction [tx (deref de)]
    (jdbc/execute! tx (sql/format sqlmap))))

(defn log-statement
  "Logs a copy of the generated SQL statement."
  [statement]
  (log/debug (sql/format statement))
  statement)
