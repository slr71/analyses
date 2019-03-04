(ns analyses.persistence
  (:require [korma.core :refer [select
                                delete
                                from
                                where
                                insert
                                values
                                fields
                                join
                                defentity
                                belongs-to
                                exec-raw
                                with
                                has-one]]
            [clojure.tools.logging :as log]
            [korma.db :refer [create-db default-connection]]
            [analyses.config :as config]
            [kameleon.uuids :refer [uuidify]]
            [cheshire.core :refer [parse-string generate-string]]
            [clojure-commons.exception-util :as cxu])
  (:import [java.util UUID]))

(declare users submissions badges)

(defn- create-db-spec
  "Creates the database connection spec to use when accessing the database
   using Korma."
  []
  {:classname   (config/db-driver-class)
   :subprotocol (config/db-subprotocol)
   :subname     (str "//" (config/db-host) ":" (config/db-port) "/" (config/db-name))
   :user        (config/db-user)
   :password    (config/db-password)})

(defn define-database
  "Defines the database connection to use from within Clojure."
  []
  (let [spec (create-db-spec)]
    (defonce de (create-db spec))
    (default-connection de)))

(defentity users)

(defentity submissions)

(defentity badges
  (belongs-to users {:fk :user_id})
  (belongs-to submissions {:fk :submission_id}))

(defn add-submission
  "Adds a new submission to the database."
  [submission]
  (let [new-uuid (UUID/randomUUID)]
    (exec-raw ["INSERT INTO submissions (id, submission) VALUES ( ?, CAST ( ? AS JSON ))"
               [new-uuid (cast Object (generate-string submission))]])
    new-uuid))

(defn get-submission
  "Returns a submission record. id is the UUID primary key for the submission."
  [id]
  (let [obj (first (select submissions
                     (where {:id (uuidify id)})))]
    (if obj
      (assoc obj :submission (parse-string (.getValue (:submission obj)) keyword))
      nil)))

(defn update-submission
  "Updates a submission record. id is the UUID primary key for the submission.
   submissions is the new state of the submission as a map. Adapted from
   similar code in the apps service."
  [id submission]
  (exec-raw ["UPDATE submissions SET submission = CAST ( ? AS JSON ) where id = ?"
             [(cast Object (generate-string submission)) (uuidify id)]])
  (get-submission id))

(defn delete-submission
  "Deletes a submission record. id is the UUID primary key for the submission."
  [id]
  (delete submissions (where {:id (uuidify id)}))
  {:id id})

(defn- get-user
  [username]
  (:id (first (select users (fields :id) (where {:username username})))))

(defn get-badge
  "Returns badge information. id is the UUID primary key for the badge."
  [id user]
  (let [obj (first (select badges
                     (with users)
                     (with submissions)
                     (fields :id  [:users.username :user] :submissions.submission)
                     (where {:badges.id (uuidify id)
                             :user_id   (get-user user)})))]
    (if obj
      (assoc obj :submission (-> (:submission obj)
                                 (.getValue)
                                 (parse-string keyword)))
      (cxu/not-found {:id id :user user}))))

(defn add-badge
  [user submission]
  (let [new-uuid (UUID/randomUUID)]
    (insert badges (values {:id            new-uuid
                            :submission_id (add-submission submission)
                            :user_id       (get-user user)}))
    (get-badge new-uuid user)))

(defn update-badge
  [id user badge]
  (if-not (first (select badges (where {:id (uuidify id)})))
    (cxu/not-found {:id id :user user})
    (let [user-id       (get-user (:user badge))
          submission-id (add-submission (:submission badge))]
      (korma.core/update badges
                         (korma.core/set-fields {:submission_id submission-id
                                                 :user_id       user-id})
                         (where {:id (uuidify id)}))
      (get-badge id user))))

(defn delete-badge
  "Delete a badge. id is the UUID primary key for the badge."
  [id user]
  (delete badges (where {:id      (uuidify id)
                         :user_id (get-user user)}))
  {:id id})
