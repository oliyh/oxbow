(ns oxbow.core
  (:require [clojure.string :as str]
            [missionary.core :as m]))

(defn parse-single-event
  [event data-parser]
  (reduce (fn [acc kv]
            (let [[_ k v] (re-find #"(\w*):\s?(.*)" kv)
                  k (when-not (str/blank? k) (keyword (str/trim k)))]
              (if k
                (assoc acc k (when-not (str/blank? v)
                               (if (= :data k)
                                 (data-parser v)
                                 v)))
                (throw (ex-info "Something is off" {:event event})))))
          {}
          (str/split-lines (str/trim event))))

(defn sse-chunk-xform
  "Returns a transducer that retains incomplete events (i.e. no trailing \n\n)
  and returns "
  []
  (fn [rf]
    (let [incomplete (volatile! nil)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result el]
         (cond
          (and (nil? @incomplete)
               (.endsWith el "\n\n"))
          (rf result el)

          (and @incomplete (.endsWith el "\n\n"))
          (do (rf result (str @incomplete el))
              (vreset! incomplete nil))

          :else
          (vswap! incomplete str el)))))))

(defn read-chunks
  "Read from `reader` and parse chunks into SSE events (string form)"
  [reader]
  (->> (m/ap
        (let [decoder (js/TextDecoder.)]
          (loop []
            (let [result (m/? (fn [s f] (.then (.read reader) s f) #()))]
              (if (.-done result)
                (do (.cancel reader) ; not sure if this works
                    {:done? (.-done result)})
                (m/amb>
                 (let [decoded (.decode decoder (.-value result))]
                   {:complete? (.endsWith decoded "\n\n")
                    :chunk decoded})
                 (recur)))))))
       (m/eduction (comp
                    (take-while (comp not :done?))
                    (map :chunk)
                    (sse-chunk-xform)
                    ;; a chunk may contain multiple events
                    (mapcat #(str/split % #"\n\n"))))))

(def default-opts
  {:on-open  #(js/console.log "Stream connected" %)
   :on-close #(js/console.log "Stream ended")
   :on-event #(js/console.log "Message received: " %)
   :on-error #(js/console.warn "Error: " %)
   :data-parser identity
   :auto-reconnect? true
   :reconnect-timeout 2000})

(defn sse-client
  [opts]
  (let [abort-state (or (::abort-state opts)
                        (let [controller (js/AbortController.)
                              signal (.-signal controller)]
                          (atom {:controller controller
                                 :signal signal
                                 :aborted? false})))
        {:keys [uri fetch-options on-open on-event on-error on-close data-parser]} (merge default-opts opts)
        on-error (fn [e]
                   (when (and on-error (not (:aborted? @abort-state)))
                     (on-error e)))
        abort-fetch! #(do (js/console.log "Aborting connection to" uri)
                          (swap! abort-state assoc :aborted? true)
                          (.abort (:controller @abort-state)))]
    (-> (js/fetch uri (clj->js (assoc fetch-options :signal (:signal @abort-state))))
        (.then (fn [response]
                 (when on-open (on-open response))
                 ((->> (read-chunks (.. response -body getReader))
                       (m/eduction (map #(parse-single-event % data-parser)))
                       (m/reduce #(on-event %2)))
                  ;; called when the flow above reaches an end (i.e. done)
                  (fn done
                    []
                    (when on-close (on-close)))
                  ;; called when an error occurs or the flow is cancelled by its consumer
                  (fn err
                    [e]
                    (when on-error (on-error e))
                    (abort-fetch!)))))
        (.catch (fn [e]
                  (when on-error (on-error e))
                  (when on-close (on-close)))))))

(comment
 (sequence
  (comp
   (sse-chunk-xform)
   (mapcat #(str/split % #"\n\n")))
  ["thing 1\n\n"
   "thing2: "
   "also thing2\n\n"
   "thing3\n\nthing4\n\n"])

 ["thing 1\n\n"
  "thing2: "]
 )
