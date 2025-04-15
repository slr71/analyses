(ns analyses.handlers
  (:require [analyses.controllers :as ctlr]
            [ring.util.http-response :refer [ok]]))

(defn service-info
  [_]
  (ok (ctlr/service-info)))
