(ns analyses.persistence
  (:require [honeysql.core :as sql]
            [honeysql.helpers :refer :all :as helpers]
            [clojure.tools.logging :as log]
            [analyses.config :as config]
            [analyses.persistence.common :refer [query exec]]
            [analyses.uuids :refer [uuidify uuid uuidify-entry]]
            [cheshire.core :refer [parse-string generate-string]]
            [clojure-commons.exception-util :as cxu]
            [clojure-commons.core :refer [when-let*]])
  (:import [java.util UUID]))

(defn add-submission
  "Adds a new submission to the database."
  [submission]
  (let [new-uuid    (uuid)
        add-sub-sql (-> (insert-into :submissions)
                        (columns :id :submission)
                        (values [[new-uuid (sql/raw ["CAST ( '" (generate-string submission) "' AS JSON )"])]]))]
    (log/debug add-sub-sql)
    (exec add-sub-sql)
    new-uuid))

(defn get-submission
  "Returns a submission record. id is the UUID primary key for the submission."
  [id]
  (let [obj (first (query (-> (select :*)
                              (from :submissions)
                              (where [:= :id (uuidify id)]))))]
    (if obj
      (assoc obj :submission (parse-string (.getValue (:submission obj)) keyword))
      nil)))

(defn update-submission
  "Updates a submission record. id is the UUID primary key for the submission.
   submissions is the new state of the submission as a map. Adapted from
   similar code in the apps service."
  [id submission]
  (let [update-sql (-> (update :submissions)
                       (sset {:submission (sql/raw ["CAST ( '" (generate-string submission) "' AS JSON )"])})
                       (where [:= :id (uuidify id)]))]
    (exec update-sql)
    (get-submission id)))

(defn delete-submission
  "Deletes a submission record. id is the UUID primary key for the submission."
  [id]
  (log/debug
   (exec (-> (delete-from :submissions)
             (where [:= :id (uuidify id)]))))
  {:id id})

(defn- get-user
  [username]
  (:id (first (query (-> (select :id)
                         (from :users)
                         (where [:= :username username]))))))

(defn get-quicklaunch
  "Returns quick launch information. id is the UUID primary key for the quick launch"
  [id user]
  (let [user-id (get-user user)
        obj (first (query (-> (select :quick_launches.id
                                      [:users.username :creator]
                                      :quick_launches.app_id
                                      :quick_launches.name
                                      :quick_launches.description
                                      :quick_launches.is_public
                                      :submissions.submission)
                              (from :quick_launches)
                              (join :users       [:= :quick_launches.creator :users.id]
                                    :submissions [:= :quick_launches.submission_id :submissions.id])
                              (where [:= :quick_launches.id (uuidify id)]
                                     [:or [:= :quick_launches.creator user-id]
                                          [:= :quick_launches.is_public true]]))))]
    (if obj
      (assoc obj :submission (-> (:submission obj)
                                 (.getValue)
                                 (parse-string keyword)))
      (cxu/not-found {:id id :creator user}))))

(defn get-all-quicklaunches
  [user]
  (let [user-id     (get-user user)
        get-all-sql (-> (select :quick_launches.id
                                [:users.username :creator]
                                :quick_launches.app_id
                                :quick_launches.name
                                :quick_launches.description
                                :quick_launches.is_public
                                :submissions.submission)
                        (from :quick_launches)
                        (join :users [:= :quick_launches.creator :users.id]
                              :submissions [:= :quick_launches.submission_id :submissions.id])
                        (where [:or [:= :quick_launches.creator user-id]
                                    [:= :quick_launches.is_public true]]))

        results     (query get-all-sql)]
    (log/debug results)
    (mapv
     #(assoc %1 :submission (-> (:submission %1)
                                (.getValue)
                                (parse-string keyword)))
     results)))

(defn get-quicklaunches-by-app
  [app-id user]
  (let [user-id (get-user user)
        get-sql (-> (select :quick_launches.id
                            [:users.username :creator]
                            :quick_launches.app_id
                            :quick_launches.name
                            :quick_launches.description
                            :quick_launches.is_public
                            :submissions.submission)
                    (from :quick_launches)
                    (join :users [:= :quick_launches.creator :users.id]
                          :submissions [:= :quick_launches.submission_id :submissions.id])
                    (where [:or [:= :quick_launches.creator user-id]
                                [:= :quick_launches.is_public true]]
                           [:= :quick_launches.app_id app-id]))
        results (query get-sql)]
    (log/debug results)
    (mapv
     #(assoc %1 :submission (-> (:submission %1)
                                (.getValue)
                                (parse-string keyword)))
     results)))


(defn add-quicklaunch
  [user quicklaunch]
  (let [new-uuid   (uuid)
        insert-sql (-> (insert-into :quick_launches)
                       (values [{:id            new-uuid
                                 :name          (:name quicklaunch)
                                 :description   (:description quicklaunch)
                                 :app_id        (uuidify (:app_id quicklaunch))
                                 :is_public     (:is_public quicklaunch)
                                 :submission_id (add-submission (:submission quicklaunch))
                                 :creator       (get-user user)}]))]
    (exec insert-sql)
    (get-quicklaunch new-uuid user)))

(defn get-unjoined-quicklaunch
  [id user]
  (first (query (-> (select :*)
                    (from :quick_launches)
                    (where [:= :id (uuidify id)]
                           [:= :creator (get-user user)])))))

(defn merge-submission
  [ql-id user new-submission]
  (when-let* [submission-id  (:submission_id (get-unjoined-quicklaunch ql-id user))
              old-submission (:submission (get-submission submission-id))]
    (merge old-submission new-submission)))


(defn- fix-uuids
  [update-map]
  (-> update-map
      (uuidify-entry [:app_id])
      (uuidify-entry [:id])
      (uuidify-entry [:creator])
      (uuidify-entry [:submission_id])
      (uuidify-entry [:quick_launch_id])
      (uuidify-entry [:user_id])))

(defn- ql-exists?
  [id user]
  (not (nil? (first (exec (-> (select :*)
                              (from :quick_launches)
                              (where [:= :id (uuidify id)]
                                     [:= :creator (uuidify (get-user user))])))))))

(defn update-quicklaunch
  [id user ql]
  (if-not (ql-exists? id user)
    (cxu/not-found {:id id :creator user})
    (let [user-id       (get-user (or (:creator ql) user))
          submission-id (add-submission (merge-submission id user (:submission ql)))
          update-map    (fix-uuids (merge {:submission_id submission-id
                                           :creator       user-id}
                                          (select-keys ql [:name :description :app_id :is_public])))
          update-sql    (-> (update :quick_launches)
                            (sset update-map)
                            (where [:= :id      (uuidify id)]
                                   [:= :creator (get-user user)]))]

      (exec update-sql)
      (get-quicklaunch id user))))

(defn delete-quicklaunch
  "Delete a quick launch. id is the UUID primary key for the quick launch."
  [id user]
  (let [delete-sql (-> (delete-from :quick_launches)
                       (where [:= :id (uuidify id)]
                              [:= :creator (get-user user)]))]
    (log/debug (sql/format delete-sql))
    (exec delete-sql)
    {:id id}))

(defn get-all-quicklaunch-favorites
  "Returns a list of quicklaunch favorites"
  [user]
  (let [user-id     (get-user user)
        get-all-sql (-> (select :quick_launch_favorites.id
                                :quick_launch_favorites.quick_launch_id
                                [:users.username :user])
                        (from :quick_launch_favorites)
                        (join :quick_launches [:= :quick_launch_favorites.quick_launch_id :quick_launches.id]
                              :users          [:= :quick_launch_favorites.user_id :users.id])
                        (where [:= :quick_launch_favorites.user_id user-id]))]
    (log/debug get-all-sql)
    (or (query get-all-sql)
        (cxu/not-found {:user user}))))

(defn get-quicklaunch-favorite
  [user qlf-id]
  (let [user-id (get-user user)
        get-qlf-sql (-> (select :quick_launch_favorites.id
                                :quick_launch_favorites.quick_launch_id
                                [:users.username :user])
                        (from :quick_launch_favorites)
                        (join :quick_launches [:= :quick_launch_favorites.quick_launch_id :quick_launches.id]
                              :users          [:= :quick_launch_favorites.user_id :users.id])
                        (where [:= :quick_launch_favorites.user_id user-id]
                               [:= :quick_launch_favorites.id (uuidify qlf-id)]))]
    (log/debug get-qlf-sql)
    (or (first (query get-qlf-sql))
        (cxu/not-found {:id qlf-id :user user}))))

(defn add-quicklaunch-favorite
  [user quick-launch-id]
  (let [new-uuid    (uuid)
        user-id     (get-user user)
        add-qlf-sql (-> (insert-into :quick_launch_favorites)
                        (values [{:id              new-uuid
                                  :quick_launch_id (uuidify quick-launch-id)
                                  :user_id         user-id}]))]
    (exec add-qlf-sql)
    (get-quicklaunch-favorite user new-uuid)))

(defn delete-quicklaunch-favorite
  [user quick-launch-id]
  (let [user-id (get-user user)
        delete-qlf-sql (-> (delete-from :quick_launch_favorites)
                           (where [:= :quick_launch_favorites.id (uuidify quick-launch-id)]
                                  [:= :quick_launch_favorites.user_id user-id]))]
    (log/debug (sql/format delete-qlf-sql))
    (exec delete-qlf-sql)
    {:id quick-launch-id}))

(defn get-quicklaunch-user-default
  [user qlud-id]
  (let [user-id      (get-user user)
        get-qlud-sql (-> (select :qlud.id
                                 [:users.username :user]
                                 :qlud.quick_launch_id
                                 :qlud.app_id)
                         (from [:quick_launch_user_defaults :qlud])
                         (join :users [:= :qlud.user_id :users.id])
                         (where [:= :qlud.user_id user-id]
                                [:= :qlud.id (uuidify qlud-id)]))]
    (log/debug get-qlud-sql)
    (or (first (query get-qlud-sql))
        (cxu/not-found {:id qlud-id :user user}))))

(defn get-all-quicklaunch-user-defaults
  [user]
  (let [user-id (get-user user)
        all-qlud-sql (-> (select :qlud.id
                                 [:users.username :user]
                                 :qlud.quick_launch_id
                                 :qlud.app_id)
                         (from [:quick_launch_user_defaults :qlud])
                         (join :users [:= :qlud.user_id :users.id])
                         (where [:= :qlud.user_id user-id]))]
    (log/debug all-qlud-sql)
    (query all-qlud-sql)))

(defn add-quicklaunch-user-default
  [user qlud]
  (let [user-id      (get-user user)
        new-uuid     (uuid)
        add-qlud-sql (-> (insert-into :quick_launch_user_defaults)
                         (values [{:id              new-uuid
                                   :user_id         user-id
                                   :quick_launch_id (uuidify (:quick_launch_id qlud))
                                   :app_id          (uuidify (:app_id qlud))}]))]
    (log/debug add-qlud-sql)
    (exec add-qlud-sql)
    (get-quicklaunch-user-default user new-uuid)))

(defn update-quicklaunch-user-default
  [id user qlud]
  (if-not (first (exec (-> (select :*) (from :quick_launch_user_defaults) (where [:= :id (uuidify id)]))))
    (cxu/not-found {:id id :user user})
    (let [user-id         (get-user user)
          update-qlud-sql (-> (update :quick_launch_user_defaults)
                              (sset (select-keys qlud [:app_id :quick_launch_id]))
                              (where [:= :id      (uuidify id)]
                                     [:= :user_id user-id]))]

      (log/debug update-qlud-sql)
      (exec update-qlud-sql)
      (get-quicklaunch-user-default user id))))

(defn delete-quicklaunch-user-default
  [user id]
  (let [user-id (get-user user)
        delete-qlud-sql (-> (delete-from :quick_launch_user_defaults)
                            (where [:= :id (uuidify id)]
                                   [:= :user_id user-id]))]
    (log/debug (sql/format delete-qlud-sql))
    (exec delete-qlud-sql)
    {:id id}))

(defn get-quicklaunch-global-default
  [user id]
  (let [user-id (get-user user)
        get-sql (-> (select :gd.id
                            :gd.app_id
                            :gd.quick_launch_id)
                    (from [:quick_launch_global_defaults :gd])
                    (join :quick_launches [:= :gd.quick_launch_id :quick_launches.id])
                    (where [:= :gd.id (uuidify id)]
                           [:= :quick_launches.creator user-id]))]
    (log/debug (sql/format get-sql))
    (or (first (query get-sql))
        (cxu/not-found {:id id :user user}))))

(defn get-all-quicklaunch-global-defaults
  [user]
  (let [user-id     (get-user user)
        get-all-sql (-> (select :gd.id
                                :gd.app_id
                                :gd.quick_launch_id)
                        (from [:quick_launch_global_defaults :gd])
                        (join :quick_launches [:= :gd.quick_launch_id :quick_launches.id])
                        (where [:= :quick_launches.creator user-id]))]
    (log/debug (sql/format get-all-sql))
    (or (query get-all-sql)
        (cxu/not-found {:user user}))))

(defn add-quicklaunch-global-default
  [user global-default]
  (let [user-id  (get-user user)
        new-uuid (uuid)
        add-sql  (-> (insert-into :quick_launch_global_defaults)
                     (values [{:id              new-uuid
                               :app_id          (uuidify (:app_id global-default))
                               :quick_launch_id (uuidify (:quick_launch_id global-default))}]))]
    (log/debug (sql/format add-sql))
    (exec add-sql)
    (get-quicklaunch-global-default user new-uuid)))

(defn update-quicklaunch-global-default
  [id user global-default]
  (let [user-id    (get-user user)
        update-sql (-> (update :quick_launch_global_defaults)
                       (sset (fix-uuids (select-keys global-default [:app_id :quick_launch_id])))
                       (where [:= :quick_launch_global_defaults.id (uuidify id)]
                              [:in :quick_launch_global_defaults.quick_launch_id
                                   (-> (select :quick_launches.id)
                                       (from :quick_launches)
                                       (join :quick_launch_global_defaults
                                             [:= :quick_launches.id
                                                 :quick_launch_global_defaults.quick_launch_id])
                                       (where [:= :quick_launches.creator user-id]))]))]
    (log/debug (sql/format update-sql))
    (exec update-sql)
    (get-quicklaunch-global-default user id)))

(defn delete-quicklaunch-global-default
  [user id]
  (let [user-id    (get-user user)
        delete-sql (-> (delete-from :quick_launch_global_defaults)
                       (where [:= :quick_launch_global_defaults.id (uuidify id)]
                              [:in :quick_launch_global_defaults.quick_launch_id
                               (-> (select :quick_launches.id)
                                   (from :quick_launches)
                                   (join :quick_launch_global_defaults
                                         [:= :quick_launches.id
                                          :quick_launch_global_defaults.quick_launch_id])
                                   (where [:= :quick_launches.creator user-id]))]))]
    (log/debug (sql/format delete-sql))
    (exec delete-sql)
    {:id id}))
