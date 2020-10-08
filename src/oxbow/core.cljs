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
  {:on-event #(js/console.log "Message received: " %)
   :on-close #(js/console.log "Stream ended")
   :on-error #(js/console.warn "Error: " %)
   :data-parser identity})

(defn sse-client [{:keys [uri fetch-options] :as opts}]
  (let [aborted? (atom false)
        opts (-> (merge default-opts opts)
                 (update :on-error (fn [on-error]
                                     (fn [e]
                                       (when-not @aborted?
                                         (on-error e))))))
        abort-controller (js/AbortController.)
        abort-signal (.-signal abort-controller)]
    (-> (js/fetch uri (clj->js (update fetch-options :signal #(or % abort-signal))))
        (.then (fn [response]
                 (read-stream (.. response -body getReader) opts)))
        (.catch (:on-error opts)))
    {:abort #(do (reset! aborted? true)
                 (.abort abort-controller))}))
