(ns analyses.handlers
  (:require
   [clojure-commons.service :as commons-service]
   [ring.util.http-response :refer [internal-server-error ok]]))

(def svc-info
  {:desc "API endpoints for managing analyses in the Discovery Environment."
   :app-name "analyses"
   :group-id "org.cyverse"
   :art-id "analyses"
   :service "analyses"})

(defn service-info
  [{:keys [parameters server-name server-port]}]
  (let [{:keys [expecting]} (:query parameters)]
    ((if (and expecting (not= expecting (:app-name svc-info))) internal-server-error ok)
     (commons-service/get-docs-status svc-info server-name server-port "/docs/" expecting))))
