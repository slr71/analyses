(ns analyses.clients
  (:require [analyses.config :refer [data-info-base-uri apps-base-uri]]
            [medley.core :as medley]
            [clj-http.client :as http]
            [cemerick.url :refer [url]]
            [clojure-commons.error-codes :refer :all]
            [slingshot.slingshot :refer [try+ throw+]]))

(def public-user "public")

(defn apps-url
  ([components]
   (apps-url components {}))
  ([components username query]
   (-> (apply url (apps-base-uri) components)
       (assoc :query (assoc query :user (clojure.string/replace username "@iplantcollaborative.org" "")))
       (str))))

(defn data-info-url
  ([components]
   (data-info-url components {}))
  ([components username query]
   (-> (apply url (data-info-base-uri) components)
       (assoc :query (assoc query :user username))
       (str))))

(defn get-path-info
  [user {:keys [ids paths] :as params}]
  (let [not-nil? (comp not nil?)]
    (when (or (seq paths) (seq ids))
      (let [body-map   (medley/filter-vals not-nil? (merge {:ids ids} {:paths paths}))
            url-params (medley/filter-vals not-nil? (select-keys params [:validation-behavior
                                                                         :filter-include
                                                                         :filter-exclude]))]
        (:body (http/post (data-info-url ["path-info"] user url-params)
                          {:content-type :json
                           :as           :json
                           :form-params  body-map}))))))

(defn paths-publicly-accessible
  [paths]
  (try+
   (let [paths-map {:paths (if (sequential? paths) paths [paths])}]
     (get-path-info public-user paths-map))
   (catch [:status 500] e
     (if (#{ERR_NOT_READABLE ERR_DOES_NOT_EXIST} (:error_code e))
       false
       (throw+)))))

(defn get-app
  [user system-id app-id]
  (:body (http/get (apps-url ["apps" system-id app-id] user {}) {:as :json})))

(def ^:private input-multiplicities
  {"FileInput"         "single"
   "FolderInput"       "collection"
   "MultiFileSelector" "many"})

(def ^:private input-types
  (set (keys input-multiplicities)))

(defn- input?
  [{:keys [type]}]
  (input-types type))

(defn- validate-prop-value
  [config {:keys [id] :as prop}]
  (let [value (config (keyword id))]
    (if (input? prop)
      (if-not (paths-publicly-accessible value)
        (throw+ {:error_code ERR_NOT_READABLE :user public-user :path value})))))

(defn- validate-prop
  [config prop]
  (when (contains? config (keyword (:id prop)))
    (validate-prop-value config prop)))

(defn- validate-app-props
  [config props]
  (doseq [a-prop props]
    (validate-prop config a-prop)))

(defn- validate-app-group
  [config group]
  (validate-app-props config (get-in group [:parameters])))

(defn- validate-app-groups
  [config groups]
  (doseq [a-group groups]
    (validate-app-group config a-group)))

(defn validate-submission
  [submission app]
  (validate-app-groups (:config submission) (:groups app)))
