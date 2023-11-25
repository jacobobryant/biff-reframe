(ns com.biffweb.examples.reframe.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.biffweb.examples.reframe.middleware :as mid]
            [com.biffweb.examples.reframe.ui :as ui]
            [com.biffweb.examples.reframe.settings :as settings]
            [clojure.edn :as edn]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [ring.middleware.anti-forgery :as csrf]
            [cheshire.core :as cheshire]))

(defn set-foo [{:keys [session params] :as ctx}]
  (biff/submit-tx ctx
    [{:db/op :update
      :db/doc-type :user
      :xt/id (:uid session)
      :user/foo (:foo params)}])
  {:status 200
   :body {:foo (:foo params)}})

(defn set-bar [{:keys [session params] :as ctx}]
  (biff/submit-tx ctx
    [{:db/op :update
      :db/doc-type :user
      :xt/id (:uid session)
      :user/bar (:bar params)}])
  {:status 200
   :body {:bar (:bar params)}})

(defn send-message [{:keys [session] :as ctx} {:keys [text]}]
  (biff/submit-tx ctx
    [{:db/doc-type :msg
      :msg/user (:uid session)
      :msg/text text
      :msg/sent-at :db/now}]))

(defn notify-clients [{:keys [com.biffweb.examples.reframe/chat-clients]} tx]
  (doseq [[op & args] (::xt/tx-ops tx)
          :when (= op ::xt/put)
          :let [[doc] args]
          :when (contains? doc :msg/text)
          ws @chat-clients]
    (jetty/send! ws (pr-str doc))))

(defn sign-out! [{:keys [session] :as ctx}]
  {:status 204
   :session nil})

(defn init [{:keys [session biff/db] :as ctx}]
  {:status 200
   :body {:current-user (xt/entity db (:uid session))
          :csrf-token csrf/*anti-forgery-token*
          :messages (q db
                       '{:find (pull msg [*])
                         :in [t0]
                         :where [[msg :msg/sent-at t]
                                 [(<= t0 t)]]}
                       (biff/add-seconds (java.util.Date.) (* -60 10)))}})

(defn app [{:keys [session biff/db] :as ctx}]
  (ui/base
   {}
   [:div#app.flex.flex-col.grow]
   [:script {:src "/cljs/app.js"}]))

(defn ws-handler [{:keys [com.biffweb.examples.reframe/chat-clients] :as ctx}]
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! chat-clients conj ws))
        :on-text (fn [ws params]
                   (send-message ctx (edn/read-string params)))
        :on-close (fn [ws status-code reason]
                    (swap! chat-clients disj ws))}})

(def about-page
  (ui/page
   {:base/title (str "About " settings/app-name)}
   [:p "This app was made with "
    [:a.link {:href "https://biffweb.com"} "Biff"] "."]))

(defn echo [{:keys [params]}]
  {:status 200
   :headers {"content-type" "application/json"}
   :body params})

(def plugin
  {:static {"/about/" about-page}
   :routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]
            ["/init" {:get init}]
            ["/sign-out" {:post sign-out!}]
            ["/set-foo" {:post set-foo}]
            ["/set-bar" {:post set-bar}]
            ["/chat" {:get ws-handler}]]
   :api-routes [["/api/echo" {:post echo}]]
   :on-tx notify-clients})
