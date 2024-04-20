(ns game-of-biff.schema)

(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id                     :user/id]
          [:user/email                :string]
          [:user/joined-at            inst?]
          [:user/foo {:optional true} :string]
          [:user/bar {:optional true} :string]]

   :game/id :uuid
   :game [:map
          [:xt/id :game/id]
          [:game/name :string]
          [:game/created-at inst?]
          ;[:game/created-by :user/id]
          [:game/living [:set [:tuple :int :int]]]]

   :msg/id  :uuid
   :msg     [:map {:closed true}
             [:xt/id :msg/id]
             [:msg/user :user/id]
             [:msg/text :string]
             [:msg/sent-at inst?]]})

(def module
  {:schema schema})
