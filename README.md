# oxbow

A Server Sent Events (SSE) client for Clojurescript based on js/fetch

[![Clojars Project](https://img.shields.io/clojars/v/oliyh/oxbow.svg)](https://clojars.org/oliyh/oxbow)

This library uses js/fetch to expose the full functionality of HTTP and processes the SSE data for you.

It's ready for use as a standalone functional Clojurescript library and also has re-frame bindings for integration
into a re-frame application.

## Rationale

js/EventSource is generally moribund and does not support sending headers.
Discussions generally reference js/fetch as a modern alternative but it does not natively process streams.

## Usage

### Clojurescript
You can start a new connection using `sse-client`, which returns a map containing a no-arg abort function under the `:abort` key.

```clj
(require '[oxbow.core :as o])

(let [{:keys [abort]} (o/sse-client {:uri "/events"
                                     :on-event #(js/console.log "Got an event!" %)})]

  ;; events are passed to the callback
  ;; call abort to close the client

  (abort))
```

### re-frame

The re-frame bindings behave the same way, with the addition of `:id` which gives you a handle to abort with.

```clj
(require '[oxbow.re-frame :as o])
(require '[re-frame.core :as rf])

(rf/reg-event-db
  ::on-event
  (fn [db [_ {:keys [data] :as event}]]
    (update db :events conj data)))


(rf/dispatch [::o/sse-client {:id ::my-events
                              :uri "/events"
                              :on-event [::on-event]}])

;; events are passed to the callback
;; call abort to close the client

(rf/dispatch [::o/abort ::my-events])
```

## Options

```clj
{:fetch-options     {:headers {"Authorization" "xyz"}}        ;; options passed to js/fetch, see https://developer.mozilla.org/en-US/docs/Web/API/WindowOrWorkerGlobalScope/fetch
 :on-open           #(js/console.log "Stream connected" %)    ;; invoked when the stream opens
 :on-close          #(js/console.log "Stream ended")          ;; invoked when the stream ends
 :on-event          #(js/console.log "Message received: " %)  ;; invoked for every event
 :on-error          #(js/console.warn "Error: " %)            ;; invoked on error
 :data-parser       identity                                  ;; parses the `data` value of the event
 :auto-reconnect?   true                                      ;; whether it should automatically reconnect upon disconnection
 :reconnect-timeout 2000                                      ;; ms to wait before attempting reconnect
 }
```

## Development

`cider-jack-in-cljs` and open the test page http://localhost:9500/figwheel-extra-main/auto-testing

You will need to also run `(oxbow.server-stub/start-server!)` for the integration tests.

## Build
[![CircleCI](https://circleci.com/gh/oliyh/oxbow.svg?style=svg)](https://circleci.com/gh/oliyh/oxbow)

## License

Copyright © 2020 oliyh

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
