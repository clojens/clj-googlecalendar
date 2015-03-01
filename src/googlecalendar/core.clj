(ns googlecalendar.core
  (:gen-class)
  (:require (clojure [edn :as edn]
                     [string :as s]
                     [walk :refer [keywordize-keys]])
            [clojure.java.io :as io :refer [resource]]
            [plumbing.core :refer [map-keys map-vals safe-get]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [dire.core :refer [with-handler!]]
            [clj-time.periodic :refer [periodic-seq]]
            [cheshire.core :refer [parse-string]]
            [me.raynes.fs :refer [mkdirs exists? parent writeable?]]
            [googlecalendar.util.import-static :refer :all])
  (:import
   (java.text Normalizer)
   (com.google.api.client.json JsonFactory)
   (com.google.api.client.json.jackson2 JacksonFactory)
;;    (com.google.api.client.http HttpTransport)
   (com.google.api.client.auth.oauth2 Credential)
   (com.google.api.client.extensions.java6.auth.oauth2 AuthorizationCodeInstalledApp)
   (com.google.api.client.extensions.jetty.auth.oauth2 LocalServerReceiver)
   (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
   (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow$Builder
                                                 GoogleAuthorizationCodeFlow GoogleClientSecrets)
   (com.google.api.client.util DateTime Lists)
   (com.google.api.client.googleapis.batch BatchRequest)
   (com.google.api.client.googleapis.batch.json JsonBatchCallback)
   (com.google.api.client.util.store DataStoreFactory FileDataStoreFactory)
   (com.google.api.services.calendar Calendar$Builder)
   (com.google.api.services.calendar.model Calendar CalendarList Event EventDateTime Events)

   (java.io File IOException InputStreamReader)
   (javax.script ScriptEngineManager ScriptEngine)
   (java.util HashSet Set Date TimeZone)

   [clojure.reflect AsmReflector JavaReflector]))

;; really neat static import of fields and methods from camelCase to lispy-names
;; note: PI=> pi newArrayList=> (new-array-list) randomUUID=> (random-uuid) etc
;; TODO allow alternative name symbols created e.g. using [a :as b]
(import-static java.lang.Math PI)
(import-static java.util.UUID randomUUID)
(import-static java.text.Normalizer$Form NFD)
(import-static java.text.Normalizer normalize)
(import-static java.util.Collections singleton)
(import-static com.google.api.client.util.Lists newArrayList)
(import-static com.google.api.services.calendar.CalendarScopes CALENDAR)
(import-static com.google.api.client.json.jackson2.JacksonFactory getDefaultInstance)
(import-static com.google.api.client.googleapis.javanet.GoogleNetHttpTransport newTrustedTransport)


;; devel
;; (require '[alembic.still :refer [distill*]])
;; (distill* '[cheshire "5.4.0"] {:verbose false})
;; (distill* '[[dire "0.5.3"]
;;             [prismatic/plumbing "0.3.7"]] {:verbose false})
;; (require '[cheshire.core :refer :all])
;; (require '[plumbing.core :refer [map-keys map-vals]])

;; @(:config (system))

;; (def config
;;   "Config file should be in resources"
;;   (edn/read-string (slurp (.getPath (resource "config.edn")))))

;; ;; dynamic vars
;; (def ^:dynamic ^:private *home-store* ".store/calendar_data") ; parent folder within $HOME
;; (def ^:dynamic ^:private *client-secret* "client_secret.json") ; file name

;; ;; paths
;; (defn home
;;   "Use Clojure Java/IO System static method environment to obtain $HOME"
;;   [folder] (str (System/getenv "HOME") "/" folder))

;; (def resource-dir
;;   "Return project resource path folder as java File object."
;;   (parent (.getPath (resource "config.edn"))))

;; (def app-name (:name config))

;; (def home-store-dir
;;   "FIXME: I don't like the whole dynamics, tempted to to go with (system)
;;   Secret however might still be missing, this is just the parent."
;;   (do
;;     (when-not (exists? (home *home-store*))
;;       (mkdirs (home *home-store*)))
;;     (File. (home *home-store*))))

;; (def fds-factory (FileDataStoreFactory. home-store-dir))
;; (def added-calendars-using-batch new-array-list)


;; oauth2
;; (defn secrets
;;   "Takes a single file name or none and returns client secret."
;;   [& {file :file :or {file *client-secret*}}]
;;   (GoogleClientSecrets/load (get-default-instance)
;;                             (io/reader (io/file home-store-dir file))))

(defonce local-server-receiver (LocalServerReceiver.))

;; (def scopes (singleton calendar))

;; (defn auth-code-flow-builder
;;   "Constructs authorization code flow using specialized builder and usable elsewhere."
;;   [sec] (-> (GoogleAuthorizationCodeFlow$Builder.
;;              (new-trusted-transport) (get-default-instance) sec scopes)
;;             (.setDataStoreFactory fds-factory) .build))

;; (defn- authorize
;;   "Loads client authorization secrets and return AuthorizationCodeInstalledApp
;;   based on our provided GoogleAuthorizationCodeFlow builder."
;;   []
;;   (if-let [emptyfile (or (-> (secrets) .getDetails .getClientId (.startsWith "Enter"))
;;                          (-> (secrets) .getDetails .getClientSecret (.startsWith "Enter ")))]
;;     (print-warning!)
;;     (-> (AuthorizationCodeInstalledApp.
;;          (auth-code-flow-builder (secrets))
;;          local-server-receiver)
;;         (.authorize "user"))))


(def system*
  {:client (create-client app-name)})




(defn ^Event new-event
  "Creates a new Event object from a range of sane default set
  optional named parameters 'summary', dates from/to and timezone."
  [& {summary :summary start-date :start-date end-date :end-date timezone
      :timezone weeks :minutes minutes :hours hours :weeks months :months
      :or {summary (format "New Event [%s]" (random-uuid))
           start-date (c/to-date (t/today))
           end-date nil
           ; default is to set an event 1h30m in the future
           minutes 30 hours 1 weeks 0 months 0
           timezone "UTC"}}]
  {:post [(= (type %) Event)]}
  (let [zone  (TimeZone/getTimeZone timezone)
        event (new Event)
        start (DateTime. start-date zone)
        end   (DateTime. (if (nil? end-date)
                           (c/to-date (t/plus (t/today)
                                              (t/minutes minutes)
                                              (t/hours hours)
                                              (t/months months)
                                              (t/weeks weeks)))
                           (c/to-date end-date)))]
    (doto event
      (.setSummary summary)
      (.setStart (.setDateTime (new EventDateTime) start))
      (.setEnd (.setDateTime (new EventDateTime) end)))
    event))

(defprotocol PGoogleCalendar
  (create-store [this] this)
  (create-fds-factory [this] this)
  (create-secrets [this] [this that])
  (authorise [this])
  (build-auth-flow [this] [this that])
  (get-store [this] this)
  (create-client [this] [this that] [this that them])
  (->key [this] this)
  (clean-up [this] this)
  (list-events [this] this)
  (get-calendar [this] [this that])
  (list-calendars [this] this)
  (list-keys [this] this)
  (show-events [this] this)
  (new-calendar [this] this)
  (add-calendar! [this] this)
  (->clj [this] this)
  (show-calendars [this] this)
  (delete-calendar! [this] this)
  (add-event! [this] [this that])
  (update-calendar! [this] [this that] [this that more])
  )

(extend-protocol PGoogleCalendar

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  java.lang.String
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (get-store [folder] (io/file (System/getenv "HOME") folder))
  (create-fds-factory [folder] (FileDataStoreFactory. (get-store folder)))
  (create-secrets [path file]
                  (GoogleClientSecrets/load (get-default-instance)
                                            (io/reader (-> (get-store path)
                                                           (io/file file)))))
  (create-client
   [app-name folder file]
   (try
     (-> (Calendar$Builder. (new-trusted-transport)
                            (get-default-instance)
                            (authorise (build-auth-flow (create-secrets folder file)
                                                        folder)))
         (.setApplicationName app-name) .build)
     (catch IOException e (println e))))


  (new-calendar [summary] (.setSummary (new Calendar) summary))
  (add-calendar! [summary] (-> (client) .calendars (.insert (new-calendar summary)) .execute))
  (update-calendar! [oldid newcal] (-> (client) .calendars (.patch oldid newcal) .execute))
  (add-event! [id event] (-> (client) .events (.insert id event) .execute))
  (show-events [id] (-> (client) .events (.list id) .execute))

  (->key [summary] (-> (normalize summary nfd)
                       (.replaceAll "\\p{InCombiningDiacriticalMarks}|[^\\w\\s]" "")
                       (.replaceAll "[\\s-]" " ")
                       .trim
                       (.replaceAll "\\s" "-")
                       s/lower-case
                       keyword))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (build-auth-flow
   [sec folder]
   (-> (GoogleAuthorizationCodeFlow$Builder.
        (new-trusted-transport) (get-default-instance) sec (singleton calendar))
       (.setDataStoreFactory (create-fds-factory folder)) .build))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (authorise [flow] (-> (AuthorizationCodeInstalledApp. flow local-server-receiver)
                        (.authorize "user")))




  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  com.google.api.services.calendar.Calendar
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (show-calendars [client] (-> client .calendarList .list .execute))
  (->clj [client] (json->clj (show-calendars client)))
  (add-calendar! [client cal] (-> client .calendars (.insert cal) .execute))
  (add-event! [client cal event] (-> client .events (.insert (.getId cal) event) .execute))
  (update-calendar! [client ocal ncal] (-> client .calendars (.patch (.getId ocal) ncal) .execute))
  (show-events [client cal] (-> client .events (.list (.getId cal)) .execute))
  (get-calendar [client k] (-> client ->clj list-calendars k))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  clojure.lang.PersistentArrayMap
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (list-calendars [m] (into {} (map #(hash-map (->key (:summary %))
                                               (:id %))
                                    (:items m))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;   com.google.api.services.calendar.model.Calendar
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  clojure.lang.Keyword
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (update-calendar! [k ncal] (update-calendar! (cal-by-key k) ncal))
  (delete-calendar! [k] (cal-by-key k))
  (add-event! [k event] (add-event! (cal-by-key k) event))
;;   (get-calendar [k m] (k m))

  )

(type (create-client app-name ".store/calendar_data" "client_secret.json"))

(-> (:client system*) (get-calendar :solobit))



