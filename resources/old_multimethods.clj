
(defn sequence-of-events
  "FIXME: Getting tired..."
  [n]
  (map #(new-event :summary (str "Event " %2 " of " n) :end-date %1)
       (take n (periodic-seq (t/now) (t/hours 12)))
       (range 1 n)
       ))

;; (defmulti add-calendar!
;;   "Dispatches on strings and calendars to either create a new calendar through
;;   the string as summary, or earlier created calendar objects which should all
;;   contain a summary already"
;;   type)

;; (defmethod add-calendar! String [summary]
;;   (-> (client) .calendars (.insert (new-calendar summary)) .execute))

;; (defmethod add-calendar! Calendar [cal]
;;   (-> (client) .calendars (.insert cal) .execute))


;; (defmulti update-calendar!
;;   "Updates a calendar object by creating a new one, applying changes and then
;;   patching the old calendar with data from the new one. For example use as e.g:
;;   (update-calendar! \"12345@googlemail.com\" (new-calendar \"Change me\"))
;;   Takes either a string identifier and new calendar, or old and new calendar
;;   objects."
;;   (fn [x y] (type x)))

;; (defmethod update-calendar! String [oldid newcal]
;;   (-> (client) .calendars (.patch oldid newcal) .execute))

;; (defmethod update-calendar! Calendar [oldcal newcal]
;;   (-> (client) .calendars (.patch (.getId oldcal) newcal) .execute))

;(delete-calendar! "t8di454tb7ji888n9mq66ecp40@group.calendar.google.com")

;; (defmulti delete-calendar! type)

;; (defmethod delete-calendar! String [id]
;;   (-> (client) .calendars (.delete id) .execute))

;; (defmethod delete-calendar! Calendar [cal]
;;   (-> (client) .calendars (.delete (.getId cal)) .execute))

;; (defmethod delete-calendar! clojure.lang.Keyword [k]
;;   (delete-calendar! (cal-by-key k)))

;; (delete-calendar! :bsr)


;(add-event! "t8di454tb7ji888n9mq66ecp40@group.calendar.google.com" (new-event))

;; (defmulti add-event! (fn [x y] (type x)))

;; (defmethod add-event! String [id event]
;;   (-> (client) .events (.insert id event) .execute))

;; (defmethod add-event! Calendar [cal event]
;;   (-> (client) .events (.insert (.getId cal) event) .execute))

;; Allow access to calendar by string fully qualified name or object
;; (show-events (add-calendar! (new-calendar "foo"))
;; (show-events "t8di454tb7ji888n9mq66ecp40@group.calendar.google.com")



;; (defmulti show-events type)

;; (defmethod show-events String [id]
;;   (-> (client) .events (.list id) .execute))

;; (defmethod show-events Calendar [cal]
;;   (-> (client) .events (.list (.getId cal)) .execute))

;; (defmethod show-events clojure.lang.Keyword [k]
;;   (show-events (cal-by-key k)))

;; (clojure.pprint/pprint (json->clj (show-events :masonry)))

;; (def new-cal (comp show-events add-calendar! new-calendar))
