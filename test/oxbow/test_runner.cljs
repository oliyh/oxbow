;; This test runner is intended to be run from the command line
(ns oxbow.test-runner
  (:require
   [oxbow.core-test]
   [oxbow.api-test]
   [figwheel.main.testing :refer [run-tests-async]]))

(defn -main [& args]
  (run-tests-async 5000))
