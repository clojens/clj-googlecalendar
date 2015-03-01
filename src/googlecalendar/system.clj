(ns googlecalendar.system
  (:require [me.raynes.fs :refer [mkdirs exists? parent writeable?]]
            [clojure.java.io :as io :refer [resource]]
            [dire.core :refer [with-handler!]]
            (clojure [pprint :refer [pprint]]
                     [edn :as edn]
                     )
            ))

;; *command-line-args* => ("arg1" "arg2" "arg3")
;; if not, read out built-in config


; this would otherwise kick off errors
(defn resx [s] (.getPath (resource s)))

;; safety
(with-handler! #'resx
  "Deals with non-existing resources so we can safely pull string path
  when we have weird input."
  java.lang.NullPointerException
  ;;; 'e' is the exception object, 'args' are the original arguments to the task.
  (fn [e & args] (println "Cannot get the path of a resource that does not exist.")))

(defn resource?
  "Safety check, path to a resource transpiles through other fns."
  [file] (exists? (resx file)))

(defn read-config
  [filepath] (edn/read-string (slurp filepath)))

(defn fallback?
  [] (not= (:name (read-config (resx "config.edn")))
           "ChangeThisToYourCompany-AndYourAppName/1.0"))


(defn printcl
  [s] (println (clojure.string/replace (clojure.string/replace s #"[ ]{2,}" " ")
                                       #"(\\n|\\r\\n|\\r)[ ]" "")))

(defn change-me!
  "Potentially has side-effects of touch/mkdir fs operations."
  [& {name :name home :home path :path file :file conf :conf
      :or {name company-product
           home (System/getenv "HOME")
           path ".store/calendar_data"
           file "client_secret.json"
           conf "config.edn"}}]
    (let [initial {:name name
                   :conf (str (io/file home path conf))
                   :auth (str (io/file home path file))
                   }
          changed? (not= name "ChangeThisToYourCompany-AndYourAppName/1.0")
          auth? (exists? (:auth initial))
          conf? (exists? (:conf initial))
          config (when conf? (slurp (:conf initial)))
          ]
      (do
        (when-not conf?
          (do
            (pprint initial)
            (println "A copy of this map was written to disk and shall now be found.")
            (println "You MUST take care to change the :name key.")

            )
        (if auth? (println "Existing client secret json file found.")
          (printcl "Non existing client secret json file. Please adjust the path accordingly
                   and download a client_secret.json after having made a Google API for
                   Google Calendar and provided OAuth2 access for applications."))
        )

      config

      ))

;; (change-me!)

;; (me.raynes.fs/writeable? "/tmp")
;; (resx "config.edn")

(defn start
  "Trigger side-effects to ensure we have a solid baseline configuration
  to start out with."
  []
  (letfn [
          ]
(resource? "x")
    ))

