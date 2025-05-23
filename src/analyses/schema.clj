(ns analyses.schema
  (:require
   [compojure.api.sweet :refer [describe]]
   [ring.swagger.coerce :as rc]
   [ring.swagger.common :refer [value-of]]
   [schema.core :as s]
   [schema.coerce :as sc]
   [schema.utils :as su]
   [slingshot.slingshot :refer [throw+]])
  (:import [java.util UUID]))

(defn- stringify-uuids
  [v]
  (if (instance? UUID v)
    (str v)
    v))

(def ^:private custom-coercions {String stringify-uuids})

(defn- custom-coercion-matcher
  [schema]
  (or (rc/json-schema-coercion-matcher schema)
      (custom-coercions schema)))

(defn coerce
  [schema value]
  ((sc/coercer (value-of schema) custom-coercion-matcher) value))

(defn coerce!
  [schema value]
  (let [result (coerce schema value)]
    (if (su/error? result)
      (throw+ (assoc result :type :compojure.api.exception/response-validation))
      result)))

(s/defschema DeletionResponse
  {:id (describe UUID "The UUID of the resource that was deleted")})

(def QuickLaunchID (describe UUID "The UUID for a Quick Launch"))
