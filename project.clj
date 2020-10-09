(defproject oliyh/oxbow "0.1.2-SNAPSHOT"
  :description "A Server Sent Events (SSE) client for cljs based on js/fetch"
  :url "https://github.com/oliyh/oxbow"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies []
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.1"]
                                       [org.clojure/clojurescript "1.10.764"]
                                       [re-frame "1.1.1"]]}
             :dev      {:dependencies [[binaryage/devtools "1.0.2"]
                                       [com.bhauman/figwheel-main "0.2.11"]
                                       [org.clojure/tools.reader "1.2.2"]
                                       [cider/piggieback "0.4.1"]
                                       [org.clojure/tools.nrepl "0.2.13"]
                                       [io.pedestal/pedestal.service "0.5.8"]
                                       [io.pedestal/pedestal.jetty "0.5.8"]]
                        :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "test"      ["do" ["clean"] ["run" "-m" "oxbow.runner"]]})
