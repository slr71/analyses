(ns analyses.routes.settings
  (:use [common-swagger-api.schema]
        [medley.core :only [remove-vals]]
        [ring.util.http-response :only [ok]])
  (:require [analyses.persistence.settings :as persist]
            [clojure.tools.logging :as log]
            [common-swagger-api.schema.analyses :as analyses-schema]))

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
