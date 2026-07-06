(ns namepicker.core
  (:require [reagent.dom.client :as rdc]
            [re-frame.core :as rf]
            [re-frame.adapter.reagent :as reagent-adapter]
            ;; Required for their registrations.
            [namepicker.db]
            [namepicker.events]
            [namepicker.subs]
            [namepicker.views :refer [app]]))

;; Namespace load does no DOM work. The root is created lazily by mount!.
(defonce react-root (atom nil))

(def app-frame :rf/default)

(defn ^:dev/after-load mount! []
  (when-let [el (and (exists? js/document)
                     (js/document.getElementById "app"))]
    (when-not @react-root
      (reset! react-root (rdc/create-root el)))
    (rdc/render @react-root
                [rf/frame-provider {:id             app-frame
                                    :initial-events [[:namepicker/initialise]]}
                 [app]])))

(defn run []
  (rf/init! reagent-adapter/adapter)
  (mount!))
