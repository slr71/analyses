(ns analyses.routes
  (:use [common-swagger-api.routes]
        [common-swagger-api.schema]
        [common-swagger-api.schema.badges]
        [common-swagger-api.schema :only [StandardUserQueryParams]])
  (:require [ring.util.http-response :refer [ok]]
            [analyses.persistence :refer [add-badge get-badge update-badge delete-badge]]))


(defroutes badge-routes
  (POST "/badges" []
    :body        [badge NewBadge]
    :query       [{:keys [user]} StandardUserQueryParams]
    :return      Badge
    :summary     "Adds a badge to the database"
    :description "Adds a badge and corresponding submission information to the
    database. The username passed in should already exist. A new UUID will be
    assigned and returned."
    (ok (add-badge user badge)))

  (GET "/badges/:id" [id]
    :return      Badge
    :query       [{:keys [user]} StandardUserQueryParams]
    :summary     "Gets badge information from the database"
    :description "Gets the badge information from the database, including its
    UUID, the name of the user that owns it, and the submission JSON"
    (ok (get-badge id user)))

  (PUT "/badges/:id" [id]
    :body        [badge NewBadge]
    :query       [{:keys [user]} StandardUserQueryParams]
    :return      Badge
    :summary     "Modifies an existing badge"
    :description "Modifies an existing badge, allowing the caller to change
    owners and the contents of the submission JSON"
    (ok (update-badge id user badge)))

  (DELETE "/badges/:id" [id]
    :query       [{:keys [user]} StandardUserQueryParams]
    :summary     "Deletes a badge"
    :description "Deletes a badge from the database. Will returns a success
    even if called on a badge that has either already been deleted or never
    existed in the first place"
    (ok (delete-badge id user))))
