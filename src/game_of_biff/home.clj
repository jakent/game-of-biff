(ns game-of-biff.home
  (:require [clojure.string :as str]
            [com.biffweb :as biff]
            [game-of-biff.rules :as rules]
            [game-of-biff.ui :as ui]))

(def origin (atom [-1 -1]))

(def dimensions (atom {:x-count 10
                       :y-count 9}))

(defn calculate-range [offset count]
  (range offset (+ count offset)))

(defn cell [{:keys [alive]}]
  [:.flex-1.aspect-square.m-1 {:style {:backgroundColor (if alive "black" "wheat")}}])

(defn row [{:keys [y living]}]
  [:.flex
   (map (fn [x] (cell {:key x :alive (contains? living [x y])}))
        (calculate-range (first @origin) (:x-count @dimensions)))])

(defn grid [{:keys [game/living]}]
  [:.border.h-screen.w-full.flex.flex-col#game-grid
   (map (fn [y] (row {:key y :y y :living living}))
        (calculate-range (second @origin) (:y-count @dimensions)))])

(defn home-page [ctx]
  (ui/page
    ctx
    (grid nil)))

(defn game-page [{:keys [game session] :as ctx}]
  (ui/page
    ctx
    (grid game)))

(defn wrap-games [handler]
  (fn [{:keys [biff/db] :as ctx}]
    (let [games (biff/q db
                        '{:find  (pull game [*])
                          :where [[game :game/living]]})]
      (handler (assoc ctx :games games)))))

(defn wrap-game [handler]
  (fn [{:keys [games path-params foo session] :as ctx}]
    (if-some [match (->> games
                         (filter #(= (:xt/id %)
                                     (parse-uuid (:id path-params))))
                         first)]
      (handler (assoc ctx :game match))
      {:status  303
       :headers {"location" "/"}})))

(defn increment [{:keys [game uri] :as ctx}]
  (biff/submit-tx ctx
                  [{:db/op :update
                    :db/doc-type :game
                    :xt/id (:xt/id game)
                    :game/living (-> game :game/living rules/tick)}])
  {:status  303
   :headers {"location" (str/replace uri "/increment" "")}})

(def module
  {:routes ["" {:middleware [wrap-games]}
            ["/" {:get home-page}]
            ["/game/:id" {:middleware [wrap-game]}
             ["" {:get game-page}]
             ["/increment" {:post increment}]]]})
