(ns game-of-biff.background
  (:require [com.biffweb :as biff]
            [game-of-biff.rules :as rules])
  (:import (java.util Date)))

(defn every-n-seconds [n]
  (iterate #(biff/add-seconds % n) (Date.)))

(defn games-to-increment [db]
  (biff/lookup-all db :game/started? true))

(defn increment-started-games [{:keys [biff/db game-of-biff/chat-clients] :as ctx}]
  (biff/submit-tx ctx
    (->> (games-to-increment db)
         (map (fn [{:keys [xt/id game/name game/started? game/living] :as game}]
                (let [watchers (get @chat-clients id)
                      tx (cond-> (assoc game :db/doc-type :game)
                                 (not-empty watchers) (update :game/living rules/tick)
                                 (empty? watchers) (assoc :game/started? false))]
                  (printf "%s %s %s %s\n" name started? (count watchers) living)
                  tx))))))

(def module
  {:tasks  [{:task     #'increment-started-games
             :schedule #(every-n-seconds 0.5)}]})
