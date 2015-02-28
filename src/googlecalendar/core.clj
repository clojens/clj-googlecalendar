(ns googlecalendar.core
  (:gen-class)
  (:require (clojure [edn :as edn]
                     [string :as s]
                     [walk :refer [keywordize-keys]])
            [clojure.java.io :as io :refer [resource]]
            [plumbing.core :refer [map-keys map-vals]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.periodic :refer [periodic-seq]]
            [cheshire.core :refer [parse-string]]
            [me.raynes.fs :refer [mkdirs exists? parent]]
            [googlecalendar.util.import-static :refer :all])
  (:import
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
(import-static java.lang.Math PI)
(import-static java.util.UUID randomUUID)
(import-static com.google.api.client.util.Lists newArrayList)
(import-static com.google.api.client.googleapis.javanet.GoogleNetHttpTransport newTrustedTransport)
(import-static com.google.api.client.json.jackson2.JacksonFactory getDefaultInstance)
(import-static com.google.api.services.calendar.CalendarScopes CALENDAR)
(import-static java.util.Collections singleton)

;; devel
;; (require '[alembic.still :refer [distill*]])
;; (distill* '[cheshire "5.4.0"] {:verbose false})
;; (distill* '[prismatic/plumbing "0.3.7"] {:verbose false})
;; (require '[cheshire.core :refer :all])
;; (require '[plumbing.core :refer [map-keys map-vals]])


;; dynamic vars
(def ^:dynamic ^:private *home-store* ".store/calendar_sample") ; parent folder within $HOME
(def ^:dynamic ^:private *client-secret* "client_secret.json") ; file name

;; comp
(defn cs [] (str (System/getenv "HOME") "/" *home-store* "/" *client-secret*))

(defn print-warning!
  [] (io!
      (println (str
              "Overwrite the file " (cs) "with the client secrets file "  "you
              downloaded from the Quickstart tool (Application OAuth2, do not
              forget to provide the Auth Screen with an e-mail or you must
              delete the oauth authorization to create a new one. Or you could
              manually write the file you got at
              https://code.google.com/apis/console/?api into " (cs)))
               (System/exit 1)))


;; paths
(defn home
  "Use Clojure Java/IO System static method environment to obtain $HOME"
  [folder] (str (System/getenv "HOME") "/" folder))

(def resource-dir
  "Return project resource path folder as java File object."
  (parent (.getPath (resource "config.edn"))))

(def config
  "Static program settings and configuration via EDN string data reader."
  (edn/read-string (slurp (.getPath (resource "config.edn")))))

(def app-name (:name config))

(def home-store-dir
  "Variable access ensures we always have a writable storage path.
  Secret however might still be missing, this is just the parent."
  (do
    (when-not (exists? (home *home-store*))
      (mkdirs (home *home-store*)))
    (File. (home *home-store*))))

(def data-store-factory (FileDataStoreFactory. home-store-dir))
;; (def added-calendars-using-batch new-array-list)


;; oauth2
(defn secrets
  "Takes a single file name or none and returns client secret."
  [& {file :file :or {file *client-secret*}}]
  (GoogleClientSecrets/load (get-default-instance)
                            (io/reader (str home-store-dir "/" file))))

(defonce local-server-receiver (LocalServerReceiver.))

(def scopes (singleton calendar))

(defn auth-code-flow-builder
  "Constructs authorization code flow using specialized builder and usable elsewhere."
  [secrets]
  (-> (GoogleAuthorizationCodeFlow$Builder. (new-trusted-transport)
                                            (get-default-instance)
                                            secrets scopes)
      (.setDataStoreFactory data-store-factory) .build))



(defn- authorize
  "Loads client authorization secrets and return AuthorizationCodeInstalledApp
  based on our provided GoogleAuthorizationCodeFlow builder."
  []
  (if-let [emptyfile (or (-> (secrets) .getDetails .getClientId (.startsWith "Enter"))
                         (-> (secrets) .getDetails .getClientSecret (.startsWith "Enter ")))]
    (print-warning!)
    (-> (AuthorizationCodeInstalledApp.
         (auth-code-flow-builder (secrets))
         local-server-receiver)
        (.authorize "user"))))

;; (authorize)

(defn client
  "Constructs a global calendar instance. Not an actual calendar but the
  authorized client application made using the builder."
  []
  (try
    (-> (Calendar$Builder. (new-trusted-transport)
                           (get-default-instance)
                           (authorize))
        (.setApplicationName app-name) .build)
    (catch IOException e (println e))))


; (view :header "Show Calendars")

(defn show-calendars
  "Shows DataMap$Entry objects in the collection of the calendarlist."
  [] (try (-> (client) .calendarList .list .execute)))


(defn kalendar
  "Keyword calendar Google->Cheshire->Clojure
  FIXME: Double quotes?"
  [] (keywordize-keys (parse-string (.toString (show-calendars)))))
  ;; (map-vals #(s/replace % #"[\"]" "") (kalendar))


(defn keynames
  "FIXME:"
  [label]
  (-> (s/replace label #" " "-")
      (s/replace #"[:]" "")
      (s/replace #"[-]{2,}" "-")
      (s/replace #"é" "e")
      s/lower-case
      keyword))


(defn list-calendars
  "Returns a smaller list of the summaries (names) of the calendars."
  [] (map #(hash-map (keynames (:summary %)) [(:summary %) (:id %)])
          (->> (kalendar) :items)))

(defn calkeys
  [] (flatten (map keys (list-calendars))))

(calkeys)

(defn ^Calendar new-calendar
  "Returns a new Calendar object optionally with a text value
  input set as descriptive textual summary for this calendar."
  [s] (.setSummary (new Calendar) s))

;(type (new-calendar))

(defn ^Event new-event
  "Creates a new Event object from a range of sane default set
  optional named parameters 'summary', dates from/to and timezone."
  [& {summary :summary start-date :start-date end-date :end-date timezone
      :timezone weeks :minutes minutes :hours hours :weeks months :months
      :or {summary (format "New Event [%s]" (random-uuid))
           start-date (c/to-date (t/today))
           end-date nil
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


(defn sequence-of-events
  "FIXME: Getting tired..."
  [n]
  (map #(new-event :summary (str "Event " %2 " of " n) :end-date %1)
       (take n (periodic-seq (t/now) (t/hours 12)))
       (range 1 n)
       ))

;; (sequence-of-events 10)


(defn ^Calendar add-calendar!
  "Adds a calendar to the Google Calendar account remotely, hence
  the exclamation for side-effects."
  [^Calendar cal] (-> (client) .calendars (.insert cal) .execute))

;; (add-calendar! (new-calendar "Teh awesome"))

;; dispatches with side-effects remotely

(defmulti update-calendar!
  "Updates a calendar object by creating a new one, applying changes and then
  patching the old calendar with data from the new one. For example use as e.g:
  (update-calendar! \"12345@googlemail.com\" (new-calendar \"Change me\"))
  Takes either a string identifier and new calendar, or old and new calendar
  objects."
  (fn [x y] (type x)))

(defmethod update-calendar! String [oldid newcal]
  (-> (client) .calendars (.patch oldid newcal) .execute))

(defmethod update-calendar! Calendar [oldcal newcal]
  (-> (client) .calendars (.patch (.getId oldcal) newcal) .execute))

;(delete-calendar! "t8di454tb7ji888n9mq66ecp40@group.calendar.google.com")

(defmulti delete-calendar! type)

(defmethod delete-calendar! String [id]
  (-> (client) .calendars (.delete id) .execute))

(defmethod delete-calendar! Calendar [cal]
  (-> (client) .calendars (.delete (.getId cal)) .execute))

;(add-event! "t8di454tb7ji888n9mq66ecp40@group.calendar.google.com" (new-event))

(defmulti add-event! (fn [x y] (type x)))

(defmethod add-event! String [id event]
  (-> (client) .events (.insert id event) .execute))

(defmethod add-event! Calendar [cal event]
  (-> (client) .events (.insert (.getId cal) event) .execute))



;; Allow access to calendar by string fully qualified name or object
;; (show-events (add-calendar! (new-calendar "foo"))
;; (show-events "t8di454tb7ji888n9mq66ecp40@group.calendar.google.com")
(defmulti show-events type)

(defmethod show-events String [id]
  (-> (client) .events (.list id) .execute))

(defmethod show-events Calendar [cal]
  (-> (client) .events (.list (.getId cal)) .execute))

(def new-cal (comp show-events add-calendar! new-calendar))

;; (new-cal "bsr")

