(ns oxbow.server-stub
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.sse :as sse]
            [clojure.core.async :as a]))

(defn- initialise-stream [event-channel context]
  (println "Initing stream")
  (a/go-loop [i 0]
    (println "Writing" i)
    (a/>! event-channel {:data i})
    (a/<! (a/timeout 10))
    (if (< i 10)
      (recur (inc i))
      (do (println "Closing stream")
          (a/close! event-channel)))))

(def routes
  (route/expand-routes
   #{["/events" :get (sse/start-event-stream initialise-stream)]
     ["/ping" :get {:name ::ping
                    :enter (fn [ctx]
                             (assoc ctx :response {:status 200 :body "pong"}))}]}))

(def service
  {:env                        :dev
   ::bootstrap/routes          #(deref #'routes)
   ::bootstrap/resource-path   "/public"
   ::bootstrap/type            :jetty
   ::bootstrap/port            8888
   ::bootstrap/join?           false
   ::bootstrap/allowed-origins {:creds true
                                :allowed-origins (constantly true)}})

(def events-url (format "http://localhost:%s/events" (::bootstrap/port service)))

(def with-server
  (fn [f]
    (let [server-instance (bootstrap/create-server (bootstrap/dev-interceptors (bootstrap/default-interceptors service)))]
      (try
        (bootstrap/start server-instance)
        (f)
        (finally (bootstrap/stop server-instance))))))

;; for use at the repl
(defonce server-instance (atom nil))

(defn stop-server! []
  (when-let [instance @server-instance]
    (bootstrap/stop instance)
    (reset! server-instance nil)))

(defn start-server! []
  (when @server-instance
    (stop-server!))
  (let [instance (bootstrap/create-server (bootstrap/dev-interceptors (bootstrap/default-interceptors service)))]
    (bootstrap/start instance)
    (reset! server-instance instance)))
