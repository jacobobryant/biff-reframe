(ns com.biffweb.examples.reframe.app.subs
  (:require
   [re-frame.core :as re-frame :refer [reg-sub]]))

(reg-sub
 ::email
 (fn [db]
   (get-in db [:current-user :user/email])))

(reg-sub
 ::foo
 (fn [db]
   (get-in db [:current-user :user/foo])))

(reg-sub
 ::bar
 (fn [db]
   (get-in db [:current-user :user/bar])))

(reg-sub
 ::messages
 (fn [db]
   (->> (:messages db)
        (filter (fn [{:msg/keys [sent-at]}]
                  (let [ten-minutes-ago (- (.getTime (js/Date.))
                                           (* 1000 60 10))]
                    (< ten-minutes-ago (.getTime sent-at)))))
        (sort-by :msg/sent-at #(compare %2 %1)))))

(reg-sub
 ::socket-ready
 (fn [db]
   (some? (:socket db))))

(reg-sub
 ::loading
 (fn [db]
   (:loading db)))
