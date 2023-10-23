(ns oxbow.core
  (:require [clojure.string :as str]))

(defn- concat-arrays [a b]
  (let [len (+ (.-length a) (.-length b))]
    (doto (new (.-constructor a) len)
      (.set a 0)
      (.set b (.-length a)))))

(defn- find-delimiter
  [array from]
  (let [len (.-length array)]
    (loop [from from]
      (let [i (.indexOf array 13 from)]
        (cond
          (= i -1) nil
          (> (+ i 4) len) nil
          (and (= (aget array (+ i 1)) 10)
               (= (aget array (+ i 2)) 13)
               (= (aget array (+ i 3)) 10)) i
          :else (recur (inc i)))))))

(defn- parse-kv-pairs [event data-parser]
  (try
    (reduce (fn [acc kv]
              (let [[_ k v] (re-find #"(\w*):\s?(.*)" kv)
                    k       (when-not (str/blank? k) (keyword (str/trim k)))]
                (if k
                  (assoc acc k (when-not (str/blank? v)
                                    (if (= :data k)
                                      (data-parser v)
                                      v)))
                  acc)))
            {}
            (str/split-lines event))
    (catch js/Error e
      {::error (ex-info "Failed parsing event" {:event event} e)})))

(defn- event-parser
  ([data-parser] (event-parser (js/TextDecoder.) data-parser))
  ([decoder data-parser]
   (let [buffer (atom (js/Uint8Array.))]
     (fn [chunk]
       (let [new-buffer (swap! buffer concat-arrays chunk)]
         (loop [events []
                start  0
                end    (find-delimiter new-buffer 0)]
           (if end
             (let [event  (.decode decoder (.subarray new-buffer start (+ end 4)))
                   events (conj events (parse-kv-pairs event data-parser))
                   start  (+ end 4)]
               (recur events start (find-delimiter new-buffer start)))
             (do
               (when (pos? start)
                 (swap! buffer (fn [buf] (.slice buf start))))
               events))))))))

(defn- read-stream [reader {:keys [on-event on-close on-error data-parser parse-event] :as opts}]
  (let [decoder (js/TextDecoder.)
        parse-event (or parse-event (event-parser decoder data-parser))]
    (-> (.read reader)
        (.then (fn [result]
                 (if (.-done result)
                   (when on-close (on-close))
                   (try
                     (doseq [event (parse-event (.-value result))]
                       (if-let [error (::error event)]
                         (when on-error (on-error error))
                         (on-event event)))
                     (finally
                       (read-stream reader (assoc opts :parse-event parse-event)))))))
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
        {:keys [auto-reconnect? reconnect-timeout uri fetch-options on-open] :as opts} (merge default-opts opts)
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
