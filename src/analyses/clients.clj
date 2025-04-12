(ns analyses.clients
  (:require
   [analyses.config :refer [data-info-base-uri apps-base-uri]]
   [medley.core :as medley]
   [cemerick.url :refer [url]]
   [clj-http.client :as http]
   [clojure-commons.error-codes :as ce]
   [clojure.string :as string]
   [slingshot.slingshot :refer [try+ throw+]]))

(def public-user "public")

(defn apps-url
  [components username query]
  (-> (apply url (apps-base-uri) components)
      (assoc :query (assoc query :user (string/replace username #"@.*$" "")))
      (str)))

(defn data-info-url
  [components username query]
  (-> (apply url (data-info-base-uri) components)
      (assoc :query (assoc query :user (string/replace username #"@.*$" "")))
      (str)))

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

(defn paths-accessible-by
  ([paths]
   (paths-accessible-by paths public-user))
  ([paths user]
   (try+
    (let [normalized-paths (if (sequential? paths) paths [paths])
          paths-map        {:paths normalized-paths}]
      (if (pos? (count normalized-paths))
        (get-path-info user paths-map)
        true)) ;; no paths to validate, so everything is fine
    (catch [:status 500] e
      (if (#{ce/ERR_NOT_READABLE ce/ERR_DOES_NOT_EXIST} (:error_code e))
        false
        (throw+))))))

(defn get-app
  [user system-id app-id]
  (:body (http/get (apps-url ["apps" system-id app-id] user {})
                   {:as :json})))

(defn get-app-version
  [user system-id app-id version-id]
  (:body (http/get (apps-url ["apps" system-id app-id "versions" version-id] user {})
                   {:as :json})))

(def ^:private input-multiplicities
  {"FileInput"         "single"
   "FolderInput"       "collection"
   "MultiFileSelector" "many"})

(def ^:private input-types
  (set (keys input-multiplicities)))

(defn- input?
  [{:keys [type]}]
  (input-types type))

(defn- update-prop
  [config prop]
  (let [id        (keyword (:id prop))
        get-value (if (input? prop)
                    (constantly {:path (config id)})
                    #(config id))]
    (if (contains? config id)
      (let [prop-value (get-value)]
        (assoc prop
               :value        prop-value
               :defaultValue prop-value))
      prop)))

(defn- update-app-props
  [config props]
  (map (partial update-prop config) props))

(defn- update-app-group
  [config group]
  (update-in group [:parameters] (partial update-app-props config)))

(defn- update-app-groups
  [config groups]
  (map (partial update-app-group config) groups))

(defn quick-launch-app-info
  [submission app _system-id]
  (update-in (assoc app :debug (:debug submission false))
             [:groups]
             (partial update-app-groups (:config submission))))

(defn- validate-prop-value
  [{{:keys [is_public] {:keys [config]} :submission} :quicklaunch
    :keys [user]
    :as _ql-info}

   {:keys [id] :as prop}]
  (let [value (config (keyword id))
        u     (if is_public public-user user)]
    (when (input? prop)
      (when-not (paths-accessible-by value u)
        (throw+ {:error_code ce/ERR_NOT_READABLE :user u :path value})))))

(defn validate-submission
  "The map passed in is in the format {:quicklaunch {}
                                       :app {}
                                       :system-id \"\"
                                       :user \"\"}"
  [{{:keys [groups]} :app
    {{:keys [config]} :submission} :quicklaunch
    :as ql-info}]
  (let [props (mapcat #(get-in %1 [:parameters]) groups)]
    (doseq [prop props]
      (when (contains? config (keyword (:id prop)))
        (validate-prop-value ql-info prop)))))
