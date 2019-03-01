(ns analyses.persistence
  (:require [korma.core :refer [select
                                delete
                                from
                                where
                                insert
                                values
                                fields
                                defentity
                                belongs-to
                                exec-raw
                                with
                                has-one]]
            [korma.db :refer [create-db default-connection]]
            [analyses.config :as config]
            [kameleon.uuids :refer [uuidify]]))

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
           (has-one submissions {:fk :submission_id}))

(defn add-submission
  "Adds a new submission to the database."
  [submission]
  (exec-raw ["INSERT INTO submissions (submission) VALUES ( CAST ( ? AS JSON ))"
             [(cast Object submission)]]))

(defn get-submission
  "Returns a submission record. id is the UUID primary key for the submission."
  [id]
  (select submissions
          (where {:id (uuidify id)})))

(defn update-submission
  "Updates a submission record. id is the UUID primary key for the submission.
   submissions is the new state of the submission as a map. Adapted from
   similar code in the apps service."
  [id submission]
  (exec-raw ["UPDATE jobs SET submission = CAST ( ? AS JSON ) where id = ?"
             [(cast Object submission) id]]))

(defn delete-submission
  "Deletes a submission record. id is the UUID primary key for the submission."
  [id]
  (delete submissions (where {:id (uuidify id)})))

(defn get-badge
  "Returns badge information. id is the UUID primary key for the badge."
  [id]
  (select badges
    (with users)
    (with submissions)
    (where {:badges.id (uuidify id)})))

(defn get-user
  [username]
  (:id (first (select users (fields :id) (where {:username username})))))

(defn add-badge
  [id user submission]
  (let [submission-id (add-submission submission)
        user-id (get-user user)]
    (insert badges (values {:submission_id submission-id
                            :user_id user-id}))))

(defn delete-badge
  "Delete a badge. id is the UUID primary key for the badge."
  [id]
  (delete badges (where {:id (uuidify id)})))
