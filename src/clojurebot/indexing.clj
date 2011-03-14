(ns clojurebot.indexing
  (:use [clojurebot.coreII :only [remove-nick-prefix-fn]])
  (:require [clj-http.client :as http]
            [org.danlarkin.json :as json])
  (:import (java.net URL URLEncoder)))

(def *host* "localhost")

(def *port* 9200)

(defn host-port []
  (str *host* ":" *port*))

(defn index* [place time doc]
  (http/put (format "http://%s/irc/msg/%s-%s"
                    (host-port) place time)
            {:body (json/encode doc)}))

(def page-size 4)

(defn query [string page]
  (-> (format "http://%s/irc/_search" (host-port))
      (http/get {:query-params
                 {:q string
                  :size 4
                  :from (* page page-size)
                  :sort "time:desc"}})
      (:body)
      (json/decode)
      (:hits)
      (:hits)
      ((partial map :_source))))

(defn index [bag]
  (let [{:keys [channel time] :as msg}
        (select-keys bag [:sender :channel :message :type :time])]
    (when-not (.startsWith (remove-nick-prefix-fn (:bot bag) (:message msg))
                           "search for")
      (index* (URLEncoder/encode channel) time msg))))

(defn search? [{:keys [message]}]
  (.startsWith message "search for"))

(defn search [{:keys [message] {:keys [es-host es-port]} :config}]
  (binding [*host* (or es-host *host*)
            *port* (or es-port *port*)]
    (let [x (.replaceAll message "^search for " "")
          brack (.lastIndexOf x "[")
          qstring (if (pos? brack)
                    (.trim (subs x 0 (dec brack)))
                    x)
          page (if (pos? brack)
                 (Long/parseLong (subs x (inc brack) (.lastIndexOf x "]")))
                 0)
          r (for [{:keys [channel sender time message]} (query qstring page)]
              (format "<%s:%s> %s" channel sender message))]
      (if (seq r)
        (apply str (interpose \newline r))
        "No results."))))
