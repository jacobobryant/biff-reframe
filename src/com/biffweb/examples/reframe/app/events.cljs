(ns com.biffweb.examples.reframe.app.events
  (:require
   [re-frame.core :as re-frame]
   [com.biffweb.examples.reframe.app.db :as db]
   ))

(re-frame/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))
