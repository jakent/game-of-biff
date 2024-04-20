(ns game-of-biff.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [game-of-biff.settings :as settings]
            [com.biffweb :as biff]
            [ring.middleware.anti-forgery :as csrf]
            [ring.util.response :as ring-response]
            [rum.core :as rum]))

(defn css-path []
  (if-some [last-modified (some-> (io/resource "public/css/main.css")
                                  ring-response/resource-data
                                  :last-modified
                                  (.getTime))]
    (str "/css/main.css?t=" last-modified)
    "/css/main.css"))

(defn js-path []
  (if-some [last-modified (some-> (io/resource "public/js/main.js")
                                  ring-response/resource-data
                                  :last-modified
                                  (.getTime))]
    (str "/js/main.js?t=" last-modified)
    "/js/main.js"))

(defn base [{:keys [::recaptcha] :as ctx} & body]
  (apply
    biff/base-html
    (-> ctx
        (merge #:base{:title       settings/app-name
                      :lang        "en-US"
                      :icon        "/img/glider.png"
                      :description (str settings/app-name " Description")
                      :image       "https://clojure.org/images/clojure-logo-120b.png"})
        (update :base/head (fn [head]
                             (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                      [:script {:src (js-path)}]
                                      [:script {:src "https://unpkg.com/htmx.org@1.9.10"}]
                                      [:script {:src "https://unpkg.com/htmx.org/dist/ext/ws.js"}]
                                      [:script {:src "https://unpkg.com/hyperscript.org@0.9.8"}]
                                      (when recaptcha
                                        [:script {:src   "https://www.google.com/recaptcha/api.js"
                                                  :async "async" :defer "defer"}])]
                                     head))))
    body))

(defn sidebar [{:keys [uri games game foo]}]
  [:.h-screen.w-80.p-3.pr-0.flex.flex-col.flex-grow
   [:select
    {:class    '[text-sm
                 cursor-pointer
                 focus:border-teal-600
                 focus:ring-teal-600]
     :onchange "window.location = this.value"}
    [:option {:value "/app"}
     "Select a game"]
    (for [{:keys [xt/id game/name]} games
          :let [url (str "/game/" id)]]
      [:option.cursor-pointer
       {:value    url
        :selected (str/starts-with? uri url)}
       name])]
   [:.grow]
   (when game
     (biff/form
       {:action (str uri "/increment")
        ;:hx-swap "outerHTML"
        ;:hx-target "#game-grid"
        }
       [:button.btn.w-full {:type "submit"}
        "Start"]))
   [:.h-3]])

(defn page [ctx & body]
  (base
    ctx
    [:.flex.bg-orange-50
     (when (bound? #'csrf/*anti-forgery-token*)
       {:hx-headers (cheshire/generate-string
                      {:x-csrf-token csrf/*anti-forgery-token*})})
     (sidebar ctx)
     [:.h-screen.w-full.p-3.flex.flex-col
      body]]))

(defn on-error [{:keys [status ex] :as ctx}]
  {:status  status
   :headers {"content-type" "text/html"}
   :body    (rum/render-static-markup
              (page
                ctx
                [:h1.text-lg.font-bold
                 (if (= status 404)
                   "Page not found."
                   "Something went wrong.")]))})
