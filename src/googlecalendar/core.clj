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
   (com.google.api.client.json JsonFactory)
   (com.google.api.client.json.jackson2 JacksonFactory)
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

;; import static symbols (fields/methods)
(import-static java.lang.Math PI)
(import-static java.util.UUID randomUUID)
(import-static java.text.Normalizer$Form NFD)
(import-static java.text.Normalizer normalize)
(import-static java.util.Collections singleton)
(import-static com.google.api.client.util.Lists newArrayList)
(import-static com.google.api.services.calendar.CalendarScopes CALENDAR)
(import-static com.google.api.client.json.jackson2.JacksonFactory getDefaultInstance)
(import-static com.google.api.client.googleapis.javanet.GoogleNetHttpTransport newTrustedTransport)

(defonce local-server-receiver (LocalServerReceiver.))

(def system* {:client (create-client app-name)})

(defn ^Event new-event
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

(defprotocol PGoogleCalendar
  (->key [this] this)
  (->clj [this] this)
  (authorize [this])
  (create-store [this] this)
  (create-fds-factory [this] this)
  (create-secrets [this] [this that])
  (build-auth-flow [this] [this that])
  (get-store [this] this)
  (create-client [this] [this that] [this that them])
  (clean-up [this] this)
  (list-events [this] this)
  (get-calendar [this] [this that])
  (list-calendars [this] this)
  (list-keys [this] this)
  (show-events [this] this)
  (new-calendar [this] this)
  (add-calendar! [this] this)
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
                            (authorize (build-auth-flow (create-secrets folder file)
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
  (authorize [flow] (-> (AuthorizationCodeInstalledApp. flow local-server-receiver)
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
  clojure.lang.Keyword
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (update-calendar! [k ncal] (update-calendar! (cal-by-key k) ncal))
  (delete-calendar! [k] (cal-by-key k))
  (add-event! [k event] (add-event! (cal-by-key k) event))

  )

