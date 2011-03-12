(ns clojurebot.indexing
  (:use [clojurebot.coreII :only [remove-nick-prefix-fn]])
  (:require [clj-http.client :as http]
            [org.danlarkin.json :as json])
  (:import (java.net URL URLEncoder)))

(def *host* "localhost")

(defn index* [place time doc]
  (http/put (format "http://%s:9200/irc/msg/%s-%s"
                    *host* place time)
            {:body (json/encode doc)}))

(def page-size 4)

(defn query [string page]
  (->> (http/get (format "http://%s:9200/irc/_search"
                         *host*)
                 {:query-params {:q string
                                 :size 4
                                 :from (* page page-size)
                                 :sort "time:desc"}})
       :body
       json/decode
       :hits
       :hits
       (map :_source)))

(defn index [bag]
  (let [{:keys [channel time] :as msg}
        (select-keys bag [:sender :channel :message :type :time])]
    (when-not (.startsWith (remove-nick-prefix-fn (:bot bag) (:message msg))
                           "search for")
      (index* (URLEncoder/encode channel) time msg))))

(defn search? [{:keys [message]}]
  (.startsWith message "search for"))

(defn search [{:keys [message]}]
  (let [x (.replaceAll message "^search for " "")
        brack (.lastIndexOf x "[")
        x1 (if (pos? brack)
             (.trim (subs x 0 (dec brack)))
             x)
        page (if (pos? brack)
               (Long/parseLong (subs x (inc brack) (.lastIndexOf x "]")))
               0)
        r (query x1 page)
        r (for [{:keys [channel sender time message]} r]
            (format "<%s:%s> %s" channel sender message))]
    (when (seq r)
      (apply str (interpose \newline r)))))
