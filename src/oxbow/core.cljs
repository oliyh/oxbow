(ns oxbow.core
  (:require [clojure.string :as str]))

(def sse-event-mask (re-pattern "(?s).+?\r\n\r\n"))

(defn- event-parser [data-parser]
  (let [buffer (atom "")]
    (fn [chunk]
      (let [new-buffer (swap! buffer str chunk)
            new-events (re-seq sse-event-mask new-buffer)]

        ;; clear read events from buffer
        (swap! buffer str/replace sse-event-mask "")

        (for [event new-events]
          (try (reduce (fn [acc kv]
                         (let [[_ k v] (re-find #"(\w*):\s?(.*)" kv)
                               k (when-not (str/blank? k) (keyword (str/trim k)))]
                           (if k
                             (assoc acc k (when-not (str/blank? v)
                                            (if (= :data k)
                                              (data-parser v)
                                              v)))
                             acc)))
                       {}
                       (str/split-lines (str/trim event)))
               (catch js/Error e
                 (throw (ex-info "Failed parsing event" {:event event} e)))))))))

(defn- read-stream [reader {:keys [on-event on-close on-error data-parser] :as opts}]
  (let [decoder (js/TextDecoder.)
        event-parser (event-parser data-parser)]
    (-> (.read reader)
        (.then (fn [result]
                 (if (.-done result)
                   (when on-close (on-close))

                   (try (doseq [event (event-parser (.decode decoder (.-value result)))]
                          (on-event event))
                        (catch js/Error e
                          (when on-error (on-error e)))
                        (finally
                          (read-stream reader opts))))))
        (.catch on-error))))

(def default-opts
  {:on-open  #(js/console.log "Stream connected" %)
   :on-close #(js/console.log "Stream ended")
   :on-event #(js/console.log "Message received: " %)
   :on-error #(js/console.warn "Error: " %)
   :data-parser identity
   :auto-reconnect? true
   :reconnect-timeout 2000})

(defn sse-client [opts]
  (let [abort-state (or (::abort-state opts)
                        (let [controller (js/AbortController.)
                              signal (.-signal controller)]
                          (atom {:controller controller
                                 :signal signal
                                 :aborted? false})))
        {:keys [auto-reconnect? reconnect-timeout uri fetch-options on-open]} (merge default-opts opts)
        {:keys [on-error on-close] :as opts}
        (-> opts
            (update :on-error (fn [on-error]
                                (fn [e]
                                  (when (and on-error (not (:aborted? @abort-state)))
                                    (on-error e)))))
            (update :on-close (fn [on-close]
                                (fn []
                                  (when on-close (on-close))
                                  (when (and auto-reconnect? (not (:aborted? @abort-state)))
                                    (js/console.log "Reconnecting to" uri)
                                    (js/setTimeout sse-client reconnect-timeout (assoc opts ::abort-state abort-state)))))))]
    (-> (js/fetch uri (clj->js (assoc fetch-options :signal (:signal @abort-state))))
        (.then (fn [response]
                 (when on-open (on-open response))
                 (read-stream (.. response -body getReader) opts)))
        (.catch (fn [e]
                  (when on-error (on-error e))
                  (when on-close (on-close)))))
    {:abort #(do (js/console.log "Aborting connection to" uri)
                 (swap! abort-state assoc :aborted? true)
                 (.abort (:controller @abort-state)))
     :opts opts}))
