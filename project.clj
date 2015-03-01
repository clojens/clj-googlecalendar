(defproject googlecalendar "0.1.0-SNAPSHOT"
  :description "Google Calendar remote API client for Clojure wrappers"
  :url "https://github.com/clojens/clj-googlecalendar"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [dire "0.5.3"]
                 [alembic "0.3.2"]
                 [clj-time "0.9.0"]
                 [cheshire "5.4.0"]
                 [me.raynes/fs "1.4.6"]
                 [prismatic/plumbing "0.3.7"]
                 [com.google.api-client/google-api-client "1.19.1"]
                 [com.google.oauth-client/google-oauth-client "1.19.0"]
                 [com.google.api-client/google-api-client-jackson2 "1.19.1"]
                 [com.google.oauth-client/google-oauth-client-jetty "1.19.0"]
                 [com.google.oauth-client/google-oauth-client-java6 "1.19.0"]
                 [com.google.apis/google-api-services-oauth2 "v2-rev86-1.19.1"]
                 [com.google.oauth-client/google-oauth-client-servlet "1.19.0"]
                 [com.google.apis/google-api-services-calendar "v3-rev118-1.19.1"]
                 ])
