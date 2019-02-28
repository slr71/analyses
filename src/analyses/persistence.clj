(ns analyses.persistence
  (:require [korma.core :refer :all]))


(defentity users)

(defentity submissions)

(defentity badges
  (belongs-to users {:fk :user_id})
  (has-one submissions {:fk :submission_id}))
