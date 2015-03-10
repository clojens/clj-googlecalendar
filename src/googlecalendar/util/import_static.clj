(in-ns 'googlecalendar.util)

(comment ^{:author "Stuart Sierra"
           :patch "Rob Jentzema (support lispy symbols)"
           :doc "Import static Java methods/fields into Clojure"}
  googlecalendar.util.import-static)

(defmacro import-static
  "Imports the named static fields and/or static methods of the class
  as (private) symbols in the current namespace. Returns a var in case
  of static fields and macro to call static method, so no first class
  citizens - remember. Name conversion from camelCase to lispy-names
  is performed. Class names must be fully qualified as you would with
  import in the regular fashion.
  Example:
      user=> (import-static java.lang.Math PI sqrt)
      user=> pi
      3.141592653589793
      user=> (import-static java.util.UUID randomUUID
      user=> (random-uuid)
      #uuid 1f26d25e-6692-47e0-953d-04690f388910"
  [class & fields-and-methods]
  (let [only (set (map str fields-and-methods))
        the-class (. Class forName (str class))
        static? (fn [x]
                    (. java.lang.reflect.Modifier
                       (isStatic (. x (getModifiers)))))
        statics (fn [array]
                    (set (map (memfn getName)
                              (filter static? array))))
        all-fields (statics (. the-class (getFields)))
        all-methods (statics (. the-class (getMethods)))
        fields-to-do (intersection all-fields only)
        methods-to-do (intersection all-methods only)
        make-sym (fn [string]
                     (with-meta (symbol (casing string)) {:private true}))
        import-field (fn [name]
                         (list 'def (make-sym name)
                               (list '. class (symbol name))))
        import-method (fn [name]
                          (list 'defmacro (make-sym name)
                                '[& args]
                                (list 'list ''. (list 'quote class)
                                      (list 'apply 'list
                                            (list 'quote (symbol name))
                                            'args))))]
    `(do ~@(map import-field fields-to-do)
         ~@(map import-method methods-to-do))))






;; Copyright (c) Stuart Sierra, 2008. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.
