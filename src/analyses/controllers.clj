(ns analyses.controllers
  (:require [analyses.persistence :as persist]
            [analyses.clients :as clients]))

(def system-id "de")

(defn add-quicklaunch
  [user quicklaunch]
  (let [{:keys [version_id] :as app} (clients/get-app user system-id (:app_id quicklaunch))
        quicklaunch                  (assoc quicklaunch :app_version_id version_id)]
    (clients/validate-submission
      {:quicklaunch quicklaunch
       :app         app
       :system-id   system-id
       :user        user})
    (persist/add-quicklaunch user quicklaunch)))

(defn update-quicklaunch
  [id user quicklaunch]
  (let [ql-merged  (merge (persist/get-quicklaunch id user) quicklaunch)
        ;FIXME     Lookup by app version ID
        app        (clients/get-app user system-id (:app_id ql-merged))
        submission (if (contains? quicklaunch :submission)
                     (persist/merge-submission id user (:submission quicklaunch))
                     (:submission (persist/get-submission (:submission_id (persist/get-unjoined-quicklaunch id user)))))]
    (clients/validate-submission
     {:quicklaunch (assoc ql-merged :submission submission)
      :app         app
      :system-id   system-id
      :user        user})
    (persist/update-quicklaunch id user quicklaunch)))

(defn quick-launch-app-info
  [id user]
  (let [quicklaunch (persist/get-quicklaunch id user)
        submission  (:submission quicklaunch)
        ;FIXME      Lookup by app version ID
        app         (clients/get-app user system-id (:app_id quicklaunch))]
    (clients/quick-launch-app-info submission app system-id)))
