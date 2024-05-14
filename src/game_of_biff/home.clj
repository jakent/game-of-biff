(ns game-of-biff.home
  (:require [com.biffweb :as biff]
            [game-of-biff.rules :as rules]
            [game-of-biff.ui :as ui]
            [ring.adapter.jetty9 :as jetty]
            [rum.core :as rum]
            [xtdb.api :as xt]))

(def origin (atom [-1 -1]))

(def dimensions (atom {:x-count 50
                       :y-count 45}))

(defn calculate-range [offset count]
  (range offset (+ count offset)))

(defn cell [{:keys [alive coord]}]
  [:button.flex-1.aspect-square.m-px.coord
   {:hx-patch  ""
    :hx-target "this"
    :hx-swap   "outerHTML"
    :style     {:backgroundColor (if alive "black" "wheat")}
    :name      "cell"
    :value     coord}])

(defn row [{:keys [y living]}]
  [:.flex
   (map (fn [x] (cell {:key x :alive (contains? living [x y]) :coord [x y]}))
        (calculate-range (first @origin) (:x-count @dimensions)))])

(defn grid [{:keys [game/living]}]
  [:.border.h-screen.w-full.flex.flex-col#game-grid
   (map (fn [y] (row {:key y :y y :living living}))
        (calculate-range (second @origin) (:y-count @dimensions)))])

(defn home-page [ctx]
  (ui/page
    ctx
    (grid nil)))

(defn game-page [{:keys [game] :as ctx}]
  (tap> {:game-page ctx})
  (ui/page
    ctx
    [:.flex
     {:hx-ext     "ws"
      :ws-connect (format "/game/%s/connect" (:xt/id game))}
     (grid game)]))

(defn wrap-games [handler]
  (fn [{:keys [biff/db] :as ctx}]
    (let [games (biff/q db
                        '{:find  (pull game [*])
                          :where [[game :game/living]]})]
      (handler (assoc ctx :games games)))))

(defn wrap-game [handler]
  (fn [{:keys [games path-params] :as ctx}]
    (if-some [match (->> games
                         (filter #(= (:xt/id %)
                                     (parse-uuid (:id path-params))))
                         first)]
      (handler (assoc ctx :game match))
      {:status  303
       :headers {"location" "/"}})))

(defn increment [{:keys [game uri] :as ctx}]
  (let [update (-> (update game :game/started? not)
                    (assoc :db/op :update
                           :db/doc-type :game))]
    (biff/submit-tx ctx [update])
    (ui/game-controls uri update)))

(defn click-cell [{:keys [game params] :as ctx}]
  (let [coord (-> params :cell read-string)]
    (biff/submit-tx ctx
                    [{:db/op       :update
                      :db/doc-type :game
                      :xt/id       (:xt/id game)
                      :game/living (conj (:game/living game) coord)}])
    (cell {:alive true :coord coord})))

(defn connect [{:keys [game-of-biff/chat-clients game]}]
  (let [game-id (:xt/id game)]
    {:status  101
     :headers {"upgrade"    "websocket"
               "connection" "upgrade"}
     :ws      {:on-connect (fn [ws]
                             (prn :connect (swap! chat-clients update game-id (fnil conj #{}) ws)))
               :on-close   (fn [ws _status-code _reason]
                             (prn :disconnect
                                  (swap! chat-clients
                                         (fn [chat-clients]
                                           (let [chat-clients (update chat-clients game-id disj ws)]
                                             (cond-> chat-clients
                                                     (empty? (get chat-clients game-id)) (dissoc game-id)))))))}}))

(defn on-game-update [{:keys [game-of-biff/chat-clients]} tx]
  (doseq [[op & args] (::xt/tx-ops tx)
          :when (= op ::xt/put)
          :let [[doc] args]
          :when (contains? doc :game/living)
          :let [html (rum/render-static-markup
                       (grid doc))]
          ws (get @chat-clients (:xt/id doc))]
    (jetty/send! ws html)))

(defn every-n-seconds [n]
  (iterate #(biff/add-seconds % n) (java.util.Date.)))


(defn games-to-increment [db]
  (biff/lookup-all db :game/started? true))

(defn increment-started-games [{:keys [biff/db] :as ctx}]
  (biff/submit-tx ctx
                  (->> (games-to-increment db)
                       (map (fn [game]
                              (println "from: " (:game/name game) (:game/living game))
                              (let [tx (-> (assoc game :db/doc-type :game)
                                           (update :game/living rules/tick))]
                                (println "to:   " (:game/name tx) (:game/living tx))
                                tx))))))

(def module
  {:routes ["" {:middleware [wrap-games]}
            ["/" {:get home-page}]
            ["/game/:id" {:middleware [wrap-game]}
             ["" {:get   game-page
                  :patch click-cell
                  :post  increment}]
             ["/connect" {:get connect}]]]
   :on-tx  on-game-update
   :tasks  [{:task     #'increment-started-games
             :schedule #(every-n-seconds 1)}]})
