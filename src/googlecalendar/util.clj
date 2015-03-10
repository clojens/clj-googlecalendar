(ns googlecalendar.util
  (:gen-class)
  (:require [alembic.still :refer [distill*]]
            (clojure [string :as s]
                     [set :refer :all]
                     [pprint :as pprint]
                     [set :refer [project union intersection]]
                     [data :refer [diff]]
                     [edn :as edn]
                     [pprint :refer [pprint print-table cl-format]])
            [plumbing.core :refer [map-vals map-keys]]
            [clojure.tools.namespace.find :refer [find-namespaces-in-jarfile
                                                  find-ns-decls-in-jarfile]])
  (:import (java.util.jar JarFile)
           (java.net URL)))

;; dev
(do
  (distill* '[backtick "LATEST"] {:verbose false})
  (require '[backtick :refer [template]]))

(load "util/casing")
(load "util/import_static")
;; (load "util/develop")

