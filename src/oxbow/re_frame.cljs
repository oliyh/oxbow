(ns oxbow.re-frame
  (:require [oxbow.core :as core]
            [re-frame.core :as rf]))

(rf/reg-fx
 ::abort
 (fn [abort-fn]
   (abort-fn)))

(rf/reg-event-fx
 ::abort
 (fn [{:keys [db]} [_ id]]
   (when-let [{:keys [abort]} (get-in db [::oxbow :sse-client id])]
     (merge {:db (update-in db [::oxbow :sse-client] dissoc id)}
            (when abort
              {::abort abort})))))

(defn- dispatch-callback [event-v]
  (when event-v
    #(rf/dispatch (conj event-v %))))

(rf/reg-event-fx
 ::sse-client
 (fn [{:keys [db]} [_ {:keys [id]
                       :or {id (random-uuid)}
                       :as opts}]]
   (let [opts (reduce (fn [opts event-key]
                        (if (contains? opts event-key)
                          (update opts event-key dispatch-callback)
                          opts))
                      opts
                      [:on-event :on-close :on-error])
         sse-client (core/sse-client opts)]
     {:db (assoc-in db [::oxbow :sse-client id] sse-client)})))
