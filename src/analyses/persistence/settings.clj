(ns analyses.persistence.settings
  (:require
   [analyses.persistence.common :refer [de log-statement query]]
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as log]
   [honey.sql :as sql]
   [honey.sql.helpers :as h]))

(defn list-concurrent-job-limits
  "Lists all defined concurrent job limits."
  []
  (as-> (h/select [:launcher :username] :concurrent_jobs [(sql/call := :launcher nil) :is_default]) q
    (h/from q :job_limits)
    (h/order-by q [:launcher :asc])
    (log-statement q)
    (query q)))

(defn- get-concurrent-job-limit*
  "Gets the concurrent job limit for a user."
  [tx username]
  (as-> (h/select [:launcher :username] :concurrent_jobs [(sql/call := :launcher nil) :is_default]) q
    (h/from q :job_limits)
    (h/where q [:or [:= :launcher (sql/call :regexp_replace username "-" "_")] [:= :launcher nil]])
    (h/order-by q :is_default)
    (sql/format q)
    (log/spy :debug q)
    (jdbc/query tx q)
    (first q)))

(defn get-concurrent-job-limit
  "Gets the concurrent job limit for a user. The returned job limit may either be the limit that was explicitly
   assinged to the user or the default job limit. If the default job limit is returned then the username in the
   result will be nil."
  [username]
  (jdbc/with-db-transaction [tx (deref de)]
    (get-concurrent-job-limit* tx username)))

(defn update-concurrent-job-limit*
  "Updates the concurrent job limit for a user."
  [tx username limit]
  (as-> (h/update :job_limits) q
    (h/set q {:concurrent_jobs limit})
    (h/where q [:= :launcher (sql/call :regexp_replace username "-" "_")])
    (sql/format q)
    (log/spy :debug q)
    (jdbc/execute! tx q)))

(defn insert-concurrent-job-limit*
  "Inserts a concurrent job limit for a user."
  [tx username limit]
  (as-> (h/insert-into :job_limits) q
    (h/values q [{:launcher        (sql/call :regexp_replace username "-" "_")
                  :concurrent_jobs limit}])
    (sql/format q)
    (log/spy :debug q)
    (jdbc/execute! tx q)))

(defn set-concurrent-job-limit
  "Sets the concurrent job limit for a user. The user's limit will be updated explicitly in the database even
   if the requested limit is the same as the default limit."
  [username limit]
  (jdbc/with-db-transaction [tx (deref de)]
    (if (:is_default (get-concurrent-job-limit* tx username))
      (insert-concurrent-job-limit* tx username limit)
      (update-concurrent-job-limit* tx username limit))
    (get-concurrent-job-limit* tx username)))

(defn- remove-concurrent-job-limit*
  "Removes the concurrent job limit for a user."
  [tx username]
  (as-> (h/delete-from :job_limits) q
    (h/where q [:= :launcher (sql/call :regexp_replace username "-" "_")])
    (sql/format q)
    (log/spy :debug q)
    (jdbc/execute! tx q)))

(defn remove-concurrent-job-limit
  "Removes the explicitly set concurrent job limit for a user. This will effectively return the user's limit
   to whatever the default limit is."
  [username]
  (jdbc/with-db-transaction [tx (deref de)]
    (remove-concurrent-job-limit* tx username)
    (get-concurrent-job-limit* tx nil)))
