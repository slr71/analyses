(ns analyses.uuids)

; We don't otherwise depend on kameleon right now since we're using HoneySQL,
; so I'm including this code here as well.

(defn uuid
  []
  (java.util.UUID/randomUUID))

(defmulti uuidify
  (fn [obj] (type obj)))

(defmethod uuidify java.util.UUID
  [uuid]
  uuid)

(defmethod uuidify java.lang.String
  [uuid]
  (java.util.UUID/fromString uuid))

(defmethod uuidify clojure.lang.Keyword
  [uuid]
  (uuidify (name uuid)))

(defmethod uuidify :default
  [_uuid]
  nil)

;; adapted from https://stackoverflow.com/a/26059795
(defn- contains-in?
  [m ks]
  (not= ::absent (get-in m ks ::absent)))

(defn uuidify-entry
  [m ks]
  (if (contains-in? m ks)
    (update-in m ks uuidify)
    m))
