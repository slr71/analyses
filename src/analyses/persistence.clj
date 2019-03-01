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
            [korma.db :refer [create-db default-connection]]
            [analyses.config :as config]
            [kameleon.uuids :refer [uuidify]]
            [cheshire.core :refer [parse-string generate-string]])
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
      (assoc obj :submission (parse-string (.getValue (:submission obj))))
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
  (delete submissions (where {:id (uuidify id)})))

(defn get-badge
  "Returns badge information. id is the UUID primary key for the badge."
  [id]
  (let [obj (first (select badges
                           (with users)
                           (with submissions)
                           (fields :id :user_id :users.username :submission_id :submissions.submission)
                           (where {:badges.id (uuidify id)})))]
    (if obj
      (assoc obj :submission (parse-string (.getValue (:submission obj))))
      nil)))

(defn get-user
  [username]
  (:id (first (select users (fields :id) (where {:username username})))))

(defn add-badge
  [user submission]
  (let [new-uuid (UUID/randomUUID)]
    (insert badges (values {:id            new-uuid
                            :submission_id (add-submission submission)
                            :user_id       (get-user user)}))
    new-uuid))

(defn update-badge
  [id user submission]
  (korma.core/update badges
    (korma.core/set-fields {:submission_id (add-submission submission)
                            :user_id (get-user user)})
    (where {:id (uuidify id)}))
  (get-badge (uuidify id)))

(defn delete-badge
  "Delete a badge. id is the UUID primary key for the badge."
  [id]
  (delete badges (where {:id (uuidify id)})))
