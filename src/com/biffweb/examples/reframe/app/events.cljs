(ns com.biffweb.examples.reframe.app.events
  (:require
   [ajax.core :as ajax]
   [clojure.edn :as edn]
   [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx dispatch]]
   [com.biffweb.examples.reframe.app.db :as db]
   [day8.re-frame.http-fx]))

(reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(reg-event-fx
 ::remote-init
 (fn [{:keys [db]} _]
   {:http-xhrio {:method          :get
                 :uri             "/app/init"
                 :response-format (ajax/transit-response-format)
                 :on-success      [::remote-init-success]
                 :on-fail         [::fail]}
    :db db}))

(reg-event-db
 ::remote-init-success
 (fn [db [_ result]]
   (merge db result {:loading false})))

(reg-event-fx
 ::sign-out
 (fn [{:keys [db]} _]
   {:http-xhrio {:method          :post
                 :uri             "/app/sign-out"
                 :headers         {:x-csrf-token (:csrf-token db)}
                 :format          (ajax/transit-request-format)
                 :response-format (ajax/text-response-format)
                 :on-success      [::sign-out-success]
                 :on-failure      [::fail]}
    :db db}))

(reg-event-fx
 ::sign-out-success
 (fn [{:keys [db]} _]
   (set! js/window.location "/")
   {:db db}))

(reg-event-fx
 ::noop
 (fn [{:keys [db]} _]
   {:db db}))

(reg-event-fx
 ::fail
 (fn [{:keys [db]} [_ event]]
   (js/console.error (pr-str event))
   {:db db}))

(reg-event-fx
 ::save-foo
 (fn [{:keys [db]} [_ text]]
   {:http-xhrio {:method          :post
                 :uri             "/app/set-foo"
                 :params          {:foo text}
                 :headers         {:x-csrf-token (:csrf-token db)}
                 :format          (ajax/transit-request-format)
                 :response-format (ajax/transit-response-format)
                 :on-success      [::save-foo-success]
                 :on-fail         [::fail]}
    :db db}))

(reg-event-db
 ::save-foo-success
 (fn [db [_ {:keys [foo]}]]
   (assoc-in db [:current-user :user/foo] foo)))

(reg-event-fx
 ::save-bar
 (fn [{:keys [db]} [_ text]]
   {:http-xhrio {:method          :post
                 :uri             "/app/set-bar"
                 :params          {:bar text}
                 :headers         {:x-csrf-token (:csrf-token db)}
                 :format          (ajax/transit-request-format)
                 :response-format (ajax/transit-response-format)
                 :on-success      [::save-bar-success]
                 :on-fail         [::fail]}
    :db db}))

(reg-event-db
 ::save-bar-success
 (fn [db [_ {:keys [bar]}]]
   (assoc-in db [:current-user :user/bar] bar)))

(reg-event-fx
 ::send-message
 (fn [{:keys [db]} [_ text]]
   (.send (:socket db) (pr-str {:text text}))
   {:db db}))

(reg-event-fx
 ::join-chat
 (fn [{:keys [db]} _]
   ;; This doesn't handle reconnects, which you'll probably want to do if you're actually using
   ;; websockets in a real app. See the htmx websocket extension for inspiration:
   ;; https://github.com/bigskysoftware/htmx/blob/30d3ceaf78f291b3a9d6df21f5ec24fadad47e0b/src/ext/ws.js#L258
   ;;
   ;; I also don't know if this idiomatic re-frame code or if this should be structured in some
   ;; other way.
   (let [socket (js/WebSocket. "ws://localhost:8080/app/chat")]
     (.addEventListener socket
                        "message"
                        (fn [event]
                          (dispatch [::message-received (edn/read-string (.-data event))])))
     (.addEventListener socket
                        "open"
                        (fn [event]
                          (dispatch [::socket-ready socket])))
     {:db db})))

(reg-event-db
 ::socket-ready
 (fn [db [_ socket]]
   (assoc db :socket socket)))

(reg-event-db
 ::message-received
 (fn [db [_ message]]
   (update db :messages conj message)))
