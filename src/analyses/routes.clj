(ns analyses.routes
  (:require
   [analyses.handlers :as handlers]
   [common-swagger-api.malli :refer [StatusParams StatusResponse]]
   [muuntaja.core :as m]
   [reitit.coercion.malli]
   [reitit.dev.pretty :as pretty]
   [reitit.openapi :as openapi]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.malli]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.ring.spec]
   [reitit.swagger-ui :as swagger-ui]))

(def openapi-endpoint
  ["/openapi.json"
   {:get {:no-doc  true
          :openapi {:info {:title       "my-api"
                           :description "openapi3 docs with [malli](https://github.com/metosin/malli) and reitit-ring"
                           :version     "0.0.1"}
                    :tags [{:name "status" :description "Service Status Information"}]}
          :handler (openapi/create-openapi-handler)}}])

(def status-endpoint
  ["/"
   {:tags #{"status"}
    :get  {:summary    "Service Information"
           :parameters {:query StatusParams}
           :responses  {200 {:description "Get information about the service as JSON"
                             :content     {"application/json" {:schema StatusResponse}}}}
           :handler    handlers/service-info}}])

(def app
  (ring/ring-handler
   (ring/router
    [openapi-endpoint
     status-endpoint]

    {:validate  reitit.ring.spec/validate
     :exception pretty/exception
     :data      {:coercion   reitit.coercion.malli/coercion
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
                              ;; coercing response bodys
                              coercion/coerce-response-middleware
                              ;; coercing request parameters
                              coercion/coerce-request-middleware
                              ;; multipart
                              multipart/multipart-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path   "/docs/"
      :config {:validatorUrl     nil
               :urls             [{:name "openapi", :url "/openapi.json"}]
               :urls.primaryName "openapi"
               :operationsSorter "alpha"}})
    (ring/create-default-handler))))
