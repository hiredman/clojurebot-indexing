(ns clojurebot.indexing
  (:require [clj-http.client :as http]
            [org.danlarkin.json :as json])
  (:import (java.net URL URLEncoder)))

(defn index* [place time doc]
  (http/put (doto (format "http://0.0.0.0:9200/irc/msg/%s-%s"
                          place time)
              println)
            {:body (json/encode doc)}))

(defn query [string]
  (->> (http/get (format "http://localhost:9200/irc/_search")
                 {:query-params {:q (URLEncoder/encode string)}})
       :body
       json/decode
       :hits
       :hits
       (map :_source)))

(defn index [bag]
  (let [{:keys [channel time] :as msg}
        (select-keys bag [:sender :channel :message :type :time])]
    (when-not (.startsWith (:message msg) "search for")
      (index* (URLEncoder/encode channel) time msg))))

(defn search? [{:keys [message]}]
  (.startsWith message "search for"))

(defn search [{:keys [message]}]
  (let [x (.replaceAll message "^search for " "")
        r (take 4 (query x))
        r (for [{:keys [channel sender time message]} r]
            (format "<%s:%s> %s" channel sender message))]
    (apply str (interpose \newline r))))
