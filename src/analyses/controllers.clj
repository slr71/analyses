(ns analyses.controllers
  (:require [analyses.persistence :as persist]
            [analyses.clients :as clients]))

(defn add-quicklaunch
  [user quicklaunch]
  (clients/validate-submission (:submission quicklaunch) (clients/get-app user "de" (:app_id quicklaunch)))
  (persist/add-quicklaunch user quicklaunch))

(defn update-quicklaunch
  [id user quicklaunch]
  (let [app (clients/get-app user "de" (:app_id quicklaunch))]
    (if (contains? quicklaunch :submission)
      (clients/validate-submission (persist/merge-submission id user (:submission quicklaunch)) app)
      (clients/validate-submission (persist/get-submission (:submission_id (persist/get-unjoined-quicklaunch id user))) app))
    (persist/update-quicklaunch id user quicklaunch)))
