(ns analyses.old-routes
  (:require
   [analyses.handlers :as handlers]
   [analyses.persistence :as persist]
   [analyses.controllers :as ctlr]
   [analyses.routes.settings :refer [analysis-settings-routes]]
   [analyses.schema :refer [DeletionResponse QuickLaunchID coerce!]]
   [cheshire.core :as cheshire]
   [clojure-commons.exception :refer [exception-handlers]]
   [clojure-commons.lcase-params :refer [wrap-lcase-params]]
   [clojure-commons.query-params :refer [wrap-query-params]]
   [common-swagger-api.malli :refer [StatusResponse]]
   [common-swagger-api.schema :refer [StandardUserQueryParams]]
   [common-swagger-api.schema.apps :refer [AppIdParam AppJobView]]
   [common-swagger-api.schema.quicklaunches
    :refer [NewQuickLaunch
            NewQuickLaunchFavorite
            NewQuickLaunchGlobalDefault
            NewQuickLaunchUserDefault
            QuickLaunch
            QuickLaunchFavorite
            QuickLaunchGlobalDefault
            QuickLaunchUserDefault
            UpdateQuickLaunch
            UpdateQuickLaunchGlobalDefault
            UpdateQuickLaunchUserDefault]]
   [compojure.api.sweet :refer [DELETE GET PATCH POST context defapi swagger-routes undocumented]]
   [compojure.api.middleware :refer [wrap-exceptions]]
   [compojure.route :as route]
   [muuntaja.core :as m]
   [reitit.coercion.malli]
   [reitit.openapi :as openapi]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as ring-coercion]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger-ui :as swagger-ui]
   [ring.util.http-response :refer [ok]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [service-logging.middleware :refer [log-validation-errors add-user-to-context]]))

;; Declarations for route bindings.
(declare app-prime ql user id uql nqlf ud new)

(def openapi-spec-endpoint
  "The endpoint that serves the OpenAPI specification."
  ["/openapi.json"
   {:get {:no-doc  true
          :openapi {:info {:title       "CyVerse Discovery Environment Analyses"
                           :description "API Endpoints for Managing Analyses"
                           :version     "3.0.1"}}
          :handler (openapi/create-openapi-handler)}}])

(def swagger-ui-handler
  "The handler that serves the Swagger UI."
  (swagger-ui/create-swagger-ui-handler
   {:path   "/docs/"
    :config {:validatorUrl     nil
             :urls             [{:name "openapi" :url "/openapi.json"}]
             :urls.primaryName "openapi"
             :operationsSorter "alpha"}}))

(def swagger-ui-endpoint
  "The endpoint that serves the Swagger UI."
  ["/docs/"
   {:get {:no-doc true
          :handler swagger-ui-handler}}])

(def status-endpoint
  "The endpoint that serves information about the status of the service."
  ["/"
   {:get {:summary   "Service Status Information"
          :responses {200 {:description "Returns information about the status of the service."
                           :content     {"application/json" {:schema StatusResponse}}}}
          :handler   handlers/service-info}}])

(defn api-endpoints
  []
  (->> [openapi-spec-endpoint
        swagger-ui-endpoint
        status-endpoint]
       (remove nil?)))

(def app
  (ring/ring-handler
   (ring/router
    (api-endpoints)

    {:data {:coercion   reitit.coercion.malli/coercion
            :muuntaja   m/instance
            :middleware [openapi/openapi-feature
                         ;; query-params & form-params
                         parameters/parameters-middleware
                            ;; content-negotiation
                         muuntaja/format-negotiate-middleware
                            ;; encoding response body
                         muuntaja/format-response-middleware
                            ;; exception handling
                         exception/exception-middleware
                            ;; decoding request body
                         muuntaja/format-request-middleware
                            ;; coercing exceptions
                         ring-coercion/coerce-exceptions-middleware
                            ;; coercing response body
                         ring-coercion/coerce-response-middleware
                            ;; coercing request parameters
                         ring-coercion/coerce-request-middleware]}})

   (ring/routes
    (ring/redirect-trailing-slash-handler)
    (ring/create-default-handler))))

(defn unrecognized-path-response
  "Builds the response to send for an unrecognized service path."
  []
  (cheshire/encode {:reason "unrecognized service path"}))

(defapi app-prime
  (swagger-routes
   {:ui         "/docs"
    :spec       "/swagger.json"
    :data       {:info     {:title       "Analyses API"
                            :description "Swaggerized Analyses API"}
                 :tags     [{:name "quicklaunches" :description "The API for managing quicklaunches."}
                            {:name "quicklaunch-favorites" :description "The API for managing quicklaunch favorites."}
                            {:name "quicklaunch-user-defaults" :description "The API for managing quicklaunch user defaults."}
                            {:name "quicklaunch-global-defaults" :description "The API for managing quicklaunch global defaults."}
                            {:name "settings" :description "The API for managing analysis settings."}]
                 :consumes ["application/json"]
                 :produces ["application/json"]}
    :middleware [add-user-to-context
                 wrap-query-params
                 wrap-lcase-params
                 wrap-keyword-params
                 [wrap-exceptions exception-handlers]
                 log-validation-errors]})

  (GET "/" [] (ok "yo what up\n"))

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
      (ok (coerce! QuickLaunch (ctlr/add-quicklaunch user ql))))

    (GET "/" []
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      [QuickLaunch]
      :summary     "Get all of the Quick Launches for a user"
      :description "Gets all of the Quick Launches for a user. Includes UUIDs"
      (ok (coerce! [QuickLaunch] (persist/get-all-quicklaunches user))))

    (context "/apps" []
      :tags ["quicklaunch-by-app"]

      (context "/:id" []
        :path-params [id :- AppIdParam]

        (GET "/" []
          :query [{:keys [user]} StandardUserQueryParams]
          :return [QuickLaunch]
          :summary "Get Quick Launch by the app UUID"
          :description "Returns a list of Quick Launches that the user can
            access based on the app's UUID"
          (ok (coerce! [QuickLaunch] (persist/get-quicklaunches-by-app id user))))))

    (context "/:id" []
      :path-params [id :- QuickLaunchID]

      (GET "/" []
        :return       QuickLaunch
        :query        [{:keys [user]} StandardUserQueryParams]
        :summary      "Gets Quick Launch information from the database"
        :description  "Gets the Quick Launch information from the database, including its
          UUID, the name of the user that owns it, and the submission JSON"
        (ok (coerce! QuickLaunch (persist/get-quicklaunch id user))))

      (PATCH "/" []
        :body         [uql UpdateQuickLaunch]
        :query        [{:keys [user]} StandardUserQueryParams]
        :return       QuickLaunch
        :summary      "Modifies an existing Quick Launch"
        :description  "Modifies an existing Quick Launch, allowing the caller to change
           owners and the contents of the submission JSON"
        (ok (coerce! QuickLaunch (ctlr/update-quicklaunch id user uql))))

      (DELETE "/" []
        :query        [{:keys [user]} StandardUserQueryParams]
        :return       DeletionResponse
        :summary      "Deletes a Quick Launch"
        :description  "Deletes a Quick Launch from the database. Will returns a success
          even if called on a Quick Launch that has either already been deleted or never
          existed in the first place"
        (ok (coerce! DeletionResponse (persist/delete-quicklaunch id user))))

      (GET "/app-info" []
        :query        [{:keys [user]} StandardUserQueryParams]
        :return       AppJobView
        :summary      "Returns the app info needed to create and populate the app launcher in the UI"
        :description  "Returns the app info needed to create and populate the app launcher in the UI.
          Populates the parameters with the values from the submission stored for the quick launch"
        (ok (coerce! AppJobView (ctlr/quick-launch-app-info id user))))))

  (context "/quicklaunch/favorites" []
    :tags ["quicklaunch-favorites"]

    (POST "/" []
      :body        [nqlf NewQuickLaunchFavorite]
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      QuickLaunchFavorite
      :summary     "Adds a favorite Quick Launch to the database for the user"
      :description "Adds a favorite Quick Launch to the database for the user.
        The username passed in should already exist. A new UUID will assigned to
        the favorite and returned with the rest of the record"
      (ok (coerce! QuickLaunchFavorite (persist/add-quicklaunch-favorite user (:quick_launch_id nqlf)))))

    (GET "/:id" [id]
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      QuickLaunchFavorite
      :summary     "Gets information about a favorited Quick Launch"
      :description "Gets information about a favorited Quick Launch. Returns
        a Quick Launch UUID which can be passed to the /quicklaunches endpoints
        to grab more information"
      (ok (coerce! QuickLaunchFavorite (persist/get-quicklaunch-favorite user id))))

    (GET "/" []
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      [QuickLaunchFavorite]
      :summary     "Gets all of the user's Quick Launch favorites"
      :description "Gets all of the user's Quick Launch favorites"
      (ok (coerce! [QuickLaunchFavorite] (persist/get-all-quicklaunch-favorites user))))

    (DELETE "/:id" [id]
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      DeletionResponse
      :summary     "Deletes a Quick Launch favorite"
      :description "Deletes a Quick Launch favorite. Does not delete the
        actual Quick Launch, just the entry that listed it as a favorite for the
        user"
      (ok (coerce! DeletionResponse (persist/delete-quicklaunch-favorite user id)))))

  (context "/quicklaunch/defaults/user" []
    :tags ["quicklaunch-user-defaults"]

    (POST "/" []
      :body        [ud NewQuickLaunchUserDefault]
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      QuickLaunchUserDefault
      :summary     "Add a Quick Launch user default"
      :description "Add a Quick Launch user defaults. A new UUID will be
        assigned to the user default and will be returned in the response"
      (ok (coerce! QuickLaunchUserDefault (persist/add-quicklaunch-user-default user ud))))

    (GET "/:id" [id]
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      QuickLaunchUserDefault
      :summary     "Get a Quick Launch user default"
      :description "Get a Quick Launch user default"
      (ok (coerce! QuickLaunchUserDefault (persist/get-quicklaunch-user-default user id))))

    (GET "/" []
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      [QuickLaunchUserDefault]
      :summary     "Get all of the Quick Launch user defaults for the logged
        in user"
      :description "Get all of the Quick Launch user defaults for the logged
        in user"
      (ok (coerce! [QuickLaunchUserDefault] (persist/get-all-quicklaunch-user-defaults user))))

    (PATCH "/:id" [id]
      :body         [update UpdateQuickLaunchUserDefault]
      :query        [{:keys [user]} StandardUserQueryParams]
      :return       QuickLaunchUserDefault
      :summary      "Modifies an existing Quick Launch user default"
      :description  "Modifies an existing Quick Launch user default"
      (ok (coerce! QuickLaunchUserDefault (persist/update-quicklaunch-user-default id user update))))

    (DELETE "/:id" [id]
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      DeletionResponse
      :summary     "Delete the Quick Launch user defaults"
      :description "Delete the Quick Launch user defaults"
      (ok (coerce! DeletionResponse (persist/delete-quicklaunch-user-default user id)))))

  (context "/quicklaunch/defaults/global" []
    :tags ["quicklaunch-global-defaults"]

    (POST "/" []
      :body        [new NewQuickLaunchGlobalDefault]
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      QuickLaunchGlobalDefault
      :summary     "Add a new Quick Launch global default"
      :description "Add a new Quick Launch global default. Assigns a new
        UUID."
      (ok (coerce! QuickLaunchGlobalDefault (persist/add-quicklaunch-global-default user new))))

    (GET "/:id" [id]
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      QuickLaunchGlobalDefault
      :summary     "Get a Quick Launch global default"
      :description "Get a Quick Launch global default"
      (ok (coerce! QuickLaunchGlobalDefault (persist/get-quicklaunch-global-default user id))))

    (GET "/" []
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      [QuickLaunchGlobalDefault]
      :summary     "Get all of the Quick Launch global defaults that the user
        has created"
      :description "Get all of the Quick Launch global defaults that the user
        has created"
      (ok (coerce! [QuickLaunchGlobalDefault] (persist/get-all-quicklaunch-global-defaults user))))

    (PATCH "/:id" [id]
      :body         [update UpdateQuickLaunchGlobalDefault]
      :query        [{:keys [user]} StandardUserQueryParams]
      :return       QuickLaunchGlobalDefault
      :summary      "Modifies an existing Quick Launch global default"
      :description  "Modifies an existing Quick Launch global default"
      (ok (coerce! QuickLaunchGlobalDefault (persist/update-quicklaunch-global-default id user update))))

    (DELETE "/:id" [id]
      :query       [{:keys [user]} StandardUserQueryParams]
      :return      DeletionResponse
      :summary     "Delete the Quick Launch global default"
      :description "Delete the Quick Launch global default"
      (ok (coerce! DeletionResponse (persist/delete-quicklaunch-global-default user id)))))

  (context "/settings" []
    :tags ["settings"]
    analysis-settings-routes)

  (undocumented (route/not-found (unrecognized-path-response))))
