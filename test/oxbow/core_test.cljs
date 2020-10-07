(ns oxbow.core-test
  (:require [oxbow.core :as o]
            [cljs.test :refer-macros [deftest testing is]]))

(deftest event-parser-test
  (testing "can parse a full event in one go"
    (let [event-parser (@#'o/event-parser identity)]
      (is (= [{:event "greeting"
               :data "Hello world"
               :id "abc-def-123"
               :retry "123"}]
             (event-parser "event: greeting
                            data: Hello world
                            id: abc-def-123
                            retry: 123\r\n\r\n")))))

  (testing "can parse a full event in multiple chunks"
    (let [event-parser (@#'o/event-parser identity)]
      (is (empty? (event-parser "event: greeting\r\n")))
      (is (empty? (event-parser "data: Hello world\r\n")))
      (is (empty? (event-parser "id: abc-def-123\r\n")))
      (is (= [{:event "greeting"
               :data "Hello world"
               :id "abc-def-123"
               :retry "123"}]
             (event-parser "retry: 123\r\n\r\n")))))

  (testing "can parse a minimal event"
    (let [event-parser (@#'o/event-parser identity)]
      (is (= [{:data "Hello world"}] (event-parser "data: Hello world\r\n\r\n")))))

  (testing "can parse multiple events"
    (let [event-parser (@#'o/event-parser identity)]
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
                            retry: 123\r\n\r\n"))))))
