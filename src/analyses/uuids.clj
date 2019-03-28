(ns analyses.uuids
  (:require [clojure.tools.logging :as log]))

; We don't otherwise depend on kameleon right now since we're using HoneySQL,
; so I'm including this code here as well.

(def ^:private uuid-regexes
  [#"^\p{XDigit}{8}(?:-\p{XDigit}{4}){3}-\p{XDigit}{12}$"
   #"^[at]\p{XDigit}{32}"])

(defn is-uuid?
  [id]
  (or (instance? java.util.UUID id)
      (and (string? id) (some #(re-find % id) uuid-regexes))))

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
  [uuid]
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
