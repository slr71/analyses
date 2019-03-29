(ns analyses.routes
  (:use [common-swagger-api.schema.quicklaunches]
        [common-swagger-api.schema :only [StandardUserQueryParams]]
        [analyses.schema])
  (:require [compojure.api.sweet :refer :all]
            [common-swagger-api.schema.apps :refer [AnalysisSubmission]]
            [clojure-commons.exception :refer [exception-handlers]]
            [clojure-commons.lcase-params :refer [wrap-lcase-params]]
            [clojure-commons.query-params :refer [wrap-query-params]]
            [compojure.api.middleware :refer [wrap-exceptions]]
            [ring.util.http-response :refer [ok]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [service-logging.middleware :refer [log-validation-errors add-user-to-context]]
            [analyses.persistence :as persist])
  (:import [java.util UUID]))

(defapi app
  (swagger-routes
   {:ui   "/docs"
    :spec "/swagger.json"
    :data {:info {:title       "Analyses API"
                  :description "Swaggerized Analyses API"}
           :tags [{:name "quicklaunches"               :description "The API for managing quicklaunches."}
                  {:name "quicklaunch-favorites"       :description "The API for managing quicklaunch favorites."}
                  {:name "quicklaunch-user-defaults"   :description "The API for managing quicklaunch user defaults."}
                  {:name "quicklaunch-global-defaults" :description "The API for managing quicklaunch global defaults."}]
           :consumes ["application/json"]
           :produces ["application/json"]}})

  (middleware
    [add-user-to-context
     wrap-query-params
     wrap-lcase-params
     wrap-keyword-params
     [wrap-exceptions exception-handlers]
     log-validation-errors]

    (GET "/" [] (ok (str "yo what up\n")))

    (context "/quicklaunches" []
      :tags ["quicklaunches"]

      (POST "/" []
        :body         [ql NewQuickLaunch]
        :query        [{:keys [user]} StandardUserQueryParams]
        :return       QuickLaunch
        :summary      "Adds a Quick Launch to the database"
        :description  "Adds a Quick Launch and corresponding submission information to the
        database. The username passed in should already exist. A new UUID will be
        assigned and returned."
        (ok (coerce! QuickLaunch (persist/add-quicklaunch user ql))))

      (GET "/:id" [id]
        :return       QuickLaunch
        :query        [{:keys [user]} StandardUserQueryParams]
        :summary      "Gets Quick Launch information from the database"
        :description  "Gets the Quick Launch information from the database, including its
        UUID, the name of the user that owns it, and the submission JSON"
        (ok (coerce! QuickLaunch (persist/get-quicklaunch id user))))

      (GET "/" []
        :query       [{:keys [user]} StandardUserQueryParams]
        :return      [QuickLaunch]
        :summary     "Get all of the Quick Launches for a user"
        :description "Gets all of the Quick Launches for a user. Includes UUIDs"
        (ok (coerce! [QuickLaunch] (persist/get-all-quicklaunches user))))

      (PATCH "/:id" [id]
        :body         [uql UpdateQuickLaunch]
        :query        [{:keys [user]} StandardUserQueryParams]
        :return       QuickLaunch
        :summary      "Modifies an existing Quick Launch"
        :description  "Modifies an existing Quick Launch, allowing the caller to change
        owners and the contents of the submission JSON"
        (ok (coerce! QuickLaunch (persist/update-quicklaunch id user uql))))

      (DELETE "/:id" [id]
        :query        [{:keys [user]} StandardUserQueryParams]
        :return        DeletionResponse
        :summary      "Deletes a Quick Launch"
        :description  "Deletes a Quick Launch from the database. Will returns a success
        even if called on a Quick Launch that has either already been deleted or never
        existed in the first place"
        (ok (coerce! DeletionResponse (persist/delete-quicklaunch id user)))))

    (context "/quicklaunch/favorites" []
      :tags ["quicklaunch-favorites"]

      (POST "/" []
        :body    [nqlf NewQuickLaunchFavorite]
        :query   [{:keys [user]} StandardUserQueryParams]
        :return  QuickLaunchFavorite
        :summary "Adds a favorite Quick Launch to the database for the user"
        :description "Adds a favorite Quick Launch to the database for the user.
        The username passed in should already exist. A new UUID will assigned to
        the favorite and returned with the rest of the record"
        (ok (coerce! QuickLaunchFavorite (persist/add-quicklaunch-favorite user (:quick_launch_id nqlf)))))

      (GET "/:id" [id]
        :query [{:keys [user]} StandardUserQueryParams]
        :return QuickLaunchFavorite
        :summary "Gets information about a favorited Quick Launch"
        :description "Gets information about a favorited Quick Launch. Returns
        a Quick Launch UUID which can be passed to the /quicklaunches endpoints
        to grab more information"
        (ok (coerce! QuickLaunchFavorite (persist/get-quicklaunch-favorite user id))))

      (GET "/" []
        :query [{:keys [user]} StandardUserQueryParams]
        :return [QuickLaunchFavorite]
        :summary "Gets all of the user's Quick Launch favorites"
        :description "Gets all of the user's Quick Launch favorites"
        (ok (coerce! [QuickLaunchFavorite] (persist/get-all-quicklaunch-favorites user))))

      (DELETE "/:id" [id]
        :query [{:keys [user]} StandardUserQueryParams]
        :return DeletionResponse
        :summary "Deletes a Quick Launch favorite"
        :description "Deletes a Quick Launch favorite. Does not delete the
        actual Quick Launch, just the entry that listed it as a favorite for the
        user"
        (ok (coerce! DeletionResponse (persist/delete-quicklaunch-favorite user id)))))

    (context "/quicklaunch/defaults/user" []
      :tags ["quicklaunch-user-defaults"]

      (POST "/" []
        :body  [ud NewQuickLaunchUserDefault]
        :query [{:keys [user]} StandardUserQueryParams]
        :return QuickLaunchUserDefault
        :summary "Add a Quick Launch user default"
        :description "Add a Quick Launch user defaults. A new UUID will be
        assigned to the user default and will be returned in the response"
        (ok (coerce! QuickLaunchUserDefault (persist/add-quicklaunch-user-default user ud))))

      (GET "/:id" [id]
        :query [{:keys [user]} StandardUserQueryParams]
        :return QuickLaunchUserDefault
        :summary "Get a Quick Launch user default"
        :description "Get a Quick Launch user default"
        (ok (coerce! QuickLaunchUserDefault (persist/get-quicklaunch-user-default user id))))

      (GET "/" []
        :query [{:keys [user]} StandardUserQueryParams]
        :return [QuickLaunchUserDefault]
        :summary "Get all of the Quick Launch user defaults for the logged in
        user"
        :description "Get all of the Quick Launch user defaults for the logged
        in user"
        (ok (coerce! [QuickLaunchUserDefault] (persist/get-all-quicklaunch-user-defaults user))))

      (DELETE "/:id" [id]
        :query [{:keys [user]} StandardUserQueryParams]
        :return DeletionResponse
        :summary "Delete the Quick Launch user defaults"
        :description "Delete the Quick Launch user defaults"
        (ok (coerce! DeletionResponse (persist/delete-quicklaunch-user-default user id)))))))
