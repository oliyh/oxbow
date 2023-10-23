(ns oxbow.core-test
  (:require [oxbow.core :as o]
            [cljs.test :refer-macros [deftest testing is async]]))

(defn- encode-string [s]
  (.encode (js/TextEncoder.) s))

(deftest event-parser-test
  (testing "can parse a full event in one go"
    (let [event-parser (comp (@#'o/event-parser identity) encode-string)]
      (is (= [{:event "greeting"
               :data "Hello world"
               :id "abc-def-123"
               :retry "123"}]
             (event-parser "event: greeting
                            data: Hello world
                            id: abc-def-123
                            retry: 123\r\n\r\n")))))

  (testing "can parse a full event in multiple chunks"
    (let [event-parser (comp (@#'o/event-parser identity) encode-string)]
      (is (empty? (event-parser "event: greeting\r\n")))
      (is (empty? (event-parser "data: Hello world\r\n")))
      (is (empty? (event-parser "id: abc-def-123\r\n")))
      (is (= [{:event "greeting"
               :data "Hello world"
               :id "abc-def-123"
               :retry "123"}]
             (event-parser "retry: 123\r\n\r\n")))))

  (testing "can parse a minimal event"
    (let [event-parser (comp (@#'o/event-parser identity) encode-string)]
      (is (= [{:data "Hello world"}] (event-parser "data: Hello world\r\n\r\n")))))

  (testing "can parse multiple events"
    (let [event-parser (comp (@#'o/event-parser identity) encode-string)]
      (is (= [{:event "greeting"
               :data "Hello world"
               :id "abc-def-123"
               :retry "123"}
              {:event "farewell"
               :data "Goodbye world"
               :id "abc-def-234"
               :retry "123"}]
             (event-parser "event: greeting
                            data: Hello world
                            id: abc-def-123
                            retry: 123\r\n\r\n

                            event: farewell
                            data: Goodbye world
                            id: abc-def-234
                            retry: 123\r\n\r\n")))))

  (testing "can accept custom event parser"
    (let [event-parser (comp (@#'o/event-parser #(str "prefix-" %)) encode-string)]
      (is (= [{:data "prefix-Hello world"}] (event-parser "data: Hello world\r\n\r\n"))))))

(defn- chunked-reader [chunks]
  (let [encoder (js/TextEncoder.)]
    (.getReader (js/ReadableStream. #js {:start (fn [controller]
                                                  (doseq [x chunks]
                                                    (let [bytes (cond (string? x)     (.encode encoder x)
                                                                      (sequential? x) (js/Uint8Array.from x))]
                                                      (.enqueue controller bytes)))
                                                  (.close controller))}))))

(defn- reader-for [coll]
  (chunked-reader (map #(str "data: " % "\r\n\r\n") coll)))

(defn- test-read-stream!
  "Tests whether read-stream invoked on a given reader will yield expected-data."
  [reader extra-opts expected-data done]
  (let [events (atom [])]
    (@#'o/read-stream
     reader
     (merge o/default-opts
            extra-opts
            {:on-event #(swap! events conj %)
             :on-close (fn []
                         (is (= expected-data (map :data @events)))
                         (done))}))))

(deftest read-stream-events-test
  (testing "can read a stream and call handlers"
    (testing "for events"
      (async done
             (test-read-stream! (reader-for (range 10))
                                {}
                                (map str (range 10))
                                done)))))

(deftest broken-chunks-test
  (testing "works correctly when the chunks are broken before the message boundary"
    (let [chunks ["data: {\"message\":\"lo" "rem\"}\r\n\r\ndata: {\"message\":\"ipsum\"}\r\n\r\n"]]
      (async done
             (test-read-stream! (chunked-reader chunks)
                                {:data-parser #(js->clj (js/JSON.parse %) :keywordize-keys true)}
                                [{:message "lorem"}
                                 {:message "ipsum"}]
                                done))))
  (testing "works correctly even when the chunk boundary breaks a multibyte UTF-8 code point in two parts"
    (let [chunks [[100 97 116 97 58 32 196 133 196] [135 196 153 13 10 13 10]]]
      (async done
             (test-read-stream! (chunked-reader chunks)
                                {}
                                ["ąćę"]
                                done)))))

(deftest read-stream-errors-test
  (testing "errors are sent to handler and processing carries on"
    (async done
           (let [events (atom [])
                 errors (atom [])
                 nan-error (js/Error. "Not a number!")]
             (@#'o/read-stream (reader-for ["0" "1" "abc" "3"])
              (merge o/default-opts {:data-parser #(let [v (js/parseInt %)]
                                                     (if (js/isNaN v)
                                                       (throw nan-error)
                                                       v))
                                     :on-event #(swap! events conj %)
                                     :on-error #(swap! errors conj %)
                                     :on-close (fn []
                                                 (is (= [0 1 3] (map :data @events)))
                                                 (is (= "Failed parsing event" (.-message (first @errors))))
                                                 (is (= {:event "data: abc\r\n\r\n"} (ex-data (first @errors))))
                                                 (is (= nan-error (.-cause (first @errors))))
                                                 (done))}))))))

;; =========================================
;; integration tests

(deftest integration-test
  (async done
         (let [events (atom [])]
           (o/sse-client {:uri "http://localhost:8888/events"
                          :data-parser js/parseInt
                          :auto-reconnect? false
                          :on-event #(do (js/console.log "got an event" %)
                                         (swap! events conj %))
                          :on-error (fn [e]
                                      (is false (str "Got an error: " e))
                                      (done))
                          :on-close (fn []
                                      (is (= (range 11) (map :data @events)))
                                      (done))}))))

(deftest reconnection-test
  (async done
         (let [connections (atom 0)
               events (atom [])
               abort-fn (atom nil)
               {:keys [abort]} (o/sse-client {:uri "http://localhost:8888/events"
                                              :data-parser js/parseInt
                                              :auto-reconnect? true
                                              :reconnect-timeout 100
                                              :on-event #(do (js/console.log "got an event" %)
                                                             (swap! events conj %))
                                              :on-error (fn [e]
                                                          (is false (str "Got an error: " e))
                                                          (done))
                                              :on-open #(swap! connections inc)
                                              :on-close (fn []
                                                          (when (< 1 @connections)
                                                            (is (= (concat (range 11) (range 11)) (map :data @events)))
                                                            (@abort-fn)
                                                            (done)))})]
           (reset! abort-fn abort))))
