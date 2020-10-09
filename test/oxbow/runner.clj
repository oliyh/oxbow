(ns oxbow.runner
  (:require [figwheel.main :as fig]
            [oxbow.server-stub :refer [with-server]]))

(defn- run-tests []
  (with-server
    #(fig/-main "-co" "test.cljs.edn" "-m" "oxbow.test-runner")))

(defn -main [& args]
  (run-tests))
