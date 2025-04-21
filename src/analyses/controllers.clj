(ns analyses.controllers
  (:require
   [analyses.persistence :as persist]
   [analyses.clients :as clients]))

(def system-id "de")

(defn service-info []
  {:service     "analyses"
   :description "API endpoints for managing analyses in the Discovery Environment."
   :version     "3.0.1"})

(defn add-quicklaunch
  [user {:keys [app_id app_version_id] :as quicklaunch}]
  (let [{:keys [version_id] :as app} (if app_version_id
                                       (clients/get-app-version user system-id app_id app_version_id)
                                       (clients/get-app user system-id app_id))
        quicklaunch                  (merge {:app_version_id version_id} quicklaunch)]
    (clients/validate-submission
     {:quicklaunch quicklaunch
      :app         app
      :system-id   system-id
      :user        user})
    (persist/add-quicklaunch user quicklaunch)))

(defn update-quicklaunch
  [id user quicklaunch]
  (let [ql-merged  (merge (persist/get-quicklaunch id user) quicklaunch)
        app        (clients/get-app-version user
                                            system-id
                                            (:app_id ql-merged)
                                            (:app_version_id ql-merged))
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
  (let [{:keys [app_id
                app_version_id
                submission]} (persist/get-quicklaunch id user)
        app                  (clients/get-app-version user system-id app_id app_version_id)]
    (clients/quick-launch-app-info submission app system-id)))
