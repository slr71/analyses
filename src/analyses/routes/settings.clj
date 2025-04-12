(ns analyses.routes.settings
  (:require
   [analyses.persistence.settings :as persist]
   [common-swagger-api.schema :refer [context defroutes DELETE GET PUT]]
   [common-swagger-api.schema.analyses :as analyses-schema]
   [medley.core :refer [remove-vals]]
   [ring.util.http-response :refer [ok]]))

;; Declarations for route names and parameter bindings.
(declare analysis-settings-routes username body)

(defroutes analysis-settings-routes
  (context "/concurrent-job-limits" []

    (GET "/" []
      :summary analyses-schema/ConcurrentJobLimitListingSummary
      :return analyses-schema/ConcurrentJobLimits
      :description analyses-schema/ConcurrentJobLimitListingDescription
      (ok {:limits (map (partial remove-vals nil?) (persist/list-concurrent-job-limits))}))

    (context "/:username" []
      :path-params [username :- analyses-schema/ConcurrentJobLimitUsername]

      (GET "/" []
        :summary analyses-schema/ConcurrentJobLimitRetrievalSummary
        :return analyses-schema/ConcurrentJobLimit
        :description analyses-schema/ConcurrentJobLimitRetrievalDescription
        (ok (remove-vals nil? (persist/get-concurrent-job-limit username))))

      (PUT "/" []
        :summary analyses-schema/ConcurrentJobLimitUpdateSummary
        :body [body analyses-schema/ConcurrentJobLimitUpdate]
        :return analyses-schema/ConcurrentJobLimit
        :description analyses-schema/ConcurrentJobLimitUpdateDescription
        (ok (persist/set-concurrent-job-limit username (:concurrent_jobs body))))

      (DELETE "/" []
        :summary analyses-schema/ConcurrentJobLimitRemovalSummary
        :return analyses-schema/ConcurrentJobLimit
        :description analyses-schema/ConcurrentJobLimitRemovalDescription
        (ok (remove-vals nil? (persist/remove-concurrent-job-limit username)))))))
