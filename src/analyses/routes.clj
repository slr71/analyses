(ns analyses.routes
  (:use [common-swagger-api.routes]
        [common-swagger-api.schema]
        [common-swagger-api.schema.badges])
  (:require [ring.util.http-response :refer [ok]]))


(defroutes badge-routes
  (POST "/badges" []
    :body        [body NewBadge]
    :return      Badge
    :summary     "Adds a badge to the database"
    :description "Adds a badge and corresponding submission information to the
    database. The username passed in should already exist. A new UUID will be
    assigned and returned."
    (ok "Stub"))

  (GET "/badges/:id" [id]
    :return      Badge
    :summary     "Gets badge information from the database"
    :description "Gets the badge information from the database, including its
    UUID, the name of the user that owns it, and the submission JSON"
    (ok "Stub"))

  (PUT "/badges/:id" [id]
    :body        [badge NewBadge]
    :return      Badge
    :summary     "Modifies an existing badge"
    :description "Modifies an existing badge, allowing the caller to change
    owners and the contents of the submission JSON"
    (ok "Stub"))

  (DELETE "/badges/:id" [id]
    :summary     "Deletes a badge"
    :description "Deletes a badge from the database. Will returns a success
    even if called on a badge that has either already been deleted or never
    existed in the first place"
    (ok "Stub")))
