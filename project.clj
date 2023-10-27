(defproject oliyh/oxbow "0.1.6-SNAPSHOT"
  :description "A Server Sent Events (SSE) client for cljs based on js/fetch"
  :url "https://github.com/oliyh/oxbow"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies []
  :resource-paths ["target"]
  :clean-targets ^{:protect false} ["target"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.11.1"]
                                       [org.clojure/clojurescript "1.11.121" :exclude [com.cognitect/transit-java]]
                                       [com.cognitect/transit-java "1.0.371"]
                                       [re-frame "1.3.0"]]}
             :dev      {:dependencies [[binaryage/devtools "1.0.7"]
                                       [com.bhauman/figwheel-main "0.2.18"
                                        :exclusions [org.eclipse.jetty.websocket/websocket-server
                                                     org.eclipse.jetty.websocket/websocket-servlet]]
                                       [org.eclipse.jetty.websocket/websocket-server "9.4.52.v20230823" :upgrade false]
                                       [org.eclipse.jetty.websocket/websocket-servlet "9.4.52.v20230823" :upgrade false]
                                       [io.pedestal/pedestal.service "0.6.1"]
                                       [io.pedestal/pedestal.jetty "0.6.1"]
                                       [org.clojure/tools.reader "1.3.6"]
                                       [cider/piggieback "0.5.3"]
                                       [org.clojure/tools.nrepl "0.2.13"]]
                        :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "test"      ["do" ["clean"] ["run" "-m" "oxbow.runner"]]})
