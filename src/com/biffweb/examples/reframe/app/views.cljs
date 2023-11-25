(ns com.biffweb.examples.reframe.app.views
  (:require
   [clojure.edn :as edn]
   [reagent.core :as reagent]
   [re-frame.core :as re-frame :refer [subscribe dispatch]]
   [com.biffweb.examples.reframe.app.subs :as subs]
   [com.biffweb.examples.reframe.app.events :as events]))

(defn foo-form []
  (let [text (reagent/atom "")]
    (fn []
      [:<>
       [:label.block {:for "foo"} "Foo: "
        [:span.font-mono (pr-str @(subscribe [::subs/foo]))]]
       [:div.h-1]
       [:div.flex
        [:input#foo.w-full {:type "text"
                            :name "foo"
                            :value @text
                            :on-change #(reset! text (-> % .-target .-value))
                            :on-key-down #(when (= 13 (.-which %))
                                            (dispatch [::events/save-foo @text]))}]
        [:div.w-3]
        [:button.btn {:on-click #(dispatch [::events/save-foo @text])} "Update"]]])))

;; In the original Biff example app, the foo and bar forms demonstrated two different ways to save a
;; value. In this re-frame example, they're just duplicates.
(defn bar-form []
  (let [text (reagent/atom "")]
    (fn []
      [:<>
       [:label.block {:for "bar"} "Bar: "
        [:span.font-mono (pr-str @(subscribe [::subs/bar]))]]
       [:div.h-1]
       [:div.flex
        [:input#bar.w-full {:type "text"
                            :name "bar"
                            :value @text
                            :on-change #(reset! text (-> % .-target .-value))
                            :on-key-down #(when (= 13 (.-which %))
                                            (dispatch [::events/save-bar @text]))}]
        [:div.w-3]
        [:button.btn {:on-click #(dispatch [::events/save-bar @text])} "Update"]]])))

(defn message [{:msg/keys [text sent-at] :as msg}]
  [:div.mt-3
   [:div.text-gray-600
    (.toLocaleDateString sent-at)
    " "
    (.toLocaleTimeString sent-at)]
   [:div text]])

(defn send-message []
  (let [text (reagent/atom "")]
    (fn []
      [:<>
       [:label.block {:for "message"} "Write a message"]
       [:div.h-1]
       [:textarea#message.w-full {:name "text"
                                  :value @text
                                  :on-change #(reset! text (-> % .-target .-value))}]
       [:div.h-1]
       [:div.text-sm.text-gray-600
        "Sign in with an incognito window to have a conversation with yourself."]
       [:div.h-2]
       [:div [:button.btn {:on-click (fn [event]
                                       (dispatch [::events/send-message @text])
                                       (reset! text ""))
                           :disabled (not @(subscribe [::subs/socket-ready]))}
              "Send message"]]])))

(defn chat []
  (let [messages @(subscribe [::subs/messages])]
    [:div
     [send-message]
     [:div.h-6]
     [:div (if (empty? messages)
             "No messages yet."
             "Messages sent in the past 10 minutes:")]
     [:div#messages
      (for [msg @(subscribe [::subs/messages])]
        ^{:key (:xt/id msg)} [message msg])]]))

(defn main-panel []
  [:<>
   [:div.flex-grow]
   [:div.p-3.mx-auto.max-w-screen-sm.w-full
    (if @(subscribe [::subs/loading])
      [:div.text-center "Loading..."]
      [:<>
       [:div "Signed in as " @(subscribe [::subs/email]) ". "
        [:button.text-blue-500.hover:text-blue-800
         {:on-click #(dispatch [::events/sign-out])}
         "Sign out"]
        "."]
       [:div.h-6]
       [foo-form]
       [:div.h-6]
       [bar-form]
       [:div.h-6]
       [chat]])]
   [:div.flex-grow]
   [:div.flex-grow]])
