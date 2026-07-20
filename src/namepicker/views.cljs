(ns namepicker.views
  "The name-picker markup, written as hiccup. Same house rule as todomvc: a
  view that subscribes or dispatches is a `reg-view`; a plain `defn` is for
  a helper that takes data + callbacks only. See docs/core/concepts/views.md."
  (:require [re-frame.core :as rf]))

;; A controlled text input, the workhorse behind both the new-class and
;; new-student forms. A plain `defn` on purpose: it touches no state of its
;; own, everything arrives as data + callbacks.
(defn draft-input [{:keys [draft on-change on-commit] :as props}]
  [:input
   (merge
     (dissoc props :draft :on-change :on-commit)
     {:type        "text"
      :value       (or draft "")
      :on-change   (fn [e] (on-change (.. e -target -value)))
      :on-key-down (fn [e] (when (= "Enter" (.-key e))
                             (.preventDefault e)
                             (on-commit)))})])

(rf/reg-view class-tabs []
  (let [classes    @(subscribe [:namepicker/class-list])
        current-id @(subscribe [:namepicker/current-class-id])]
    [:div.class-tabs
     (for [{:keys [id name]} classes]
       ^{:key id}
       [:div.class-tab {:class (when (= id current-id) "active")}
        [:button.class-tab-select
         {:on-click #(dispatch [:namepicker/select-class id])}
         name]
        [:button.class-tab-remove
         {:on-click #(dispatch [:namepicker/remove-class id])
          :aria-label (str "Remove class " name)}
         "×"]])]))

(rf/reg-view add-class-form []
  [:form.add-class
   {:on-submit (fn [e] (.preventDefault e) (dispatch [:namepicker.ui/commit-new-class]))}
   [draft-input
    {:placeholder "New class name"
     :draft       @(subscribe [:namepicker.ui/draft :new-class])
     :on-change   #(dispatch [:namepicker.ui/edit-draft :new-class %])
     :on-commit   #(dispatch [:namepicker.ui/commit-new-class])}]
   [:button {:type "submit"} "Add class"]])

(rf/reg-view student-row [{:keys [id name picks class-id picked?]}]
  [:li.student-row {:class (when picked? "picked")}
   [:span.student-name name]
   [:span.student-picks (str "picked " picks (if (= picks 1) " time" " times"))]
   [:button.student-remove
    {:on-click #(dispatch [:namepicker/remove-student class-id id])}
    "Remove"]])

(rf/reg-view student-list []
  (let [class-id (:id @(subscribe [:namepicker/current-class]))
        students @(subscribe [:namepicker/current-students])
        picked   @(subscribe [:namepicker/last-picked-student class-id])]
    [:ul.student-list
     (for [s students]
       ^{:key (:id s)}
       [student-row (assoc s :class-id class-id :picked? (= (:id s) (:id picked)))])]))

;; A controlled textarea. Unlike `draft-input`, Enter is left alone —
;; typing a newline is how you separate names, so only the submit button
;; (or Cmd/Ctrl+Enter) commits.
(defn draft-textarea [{:keys [draft on-change on-commit] :as props}]
  [:textarea
   (merge
     (dissoc props :draft :on-change :on-commit)
     {:value       (or draft "")
      :on-change   (fn [e] (on-change (.. e -target -value)))
      :on-key-down (fn [e] (when (and (= "Enter" (.-key e)) (or (.-metaKey e) (.-ctrlKey e)))
                             (.preventDefault e)
                             (on-commit)))})])

(rf/reg-view add-student-form []
  (let [class-id (:id @(subscribe [:namepicker/current-class]))]
    [:form.add-student
     {:on-submit (fn [e] (.preventDefault e) (dispatch [:namepicker.ui/commit-new-students class-id]))}
     [draft-textarea
      {:placeholder "One student per line (or comma-separated) — paste a whole roster at once"
       :rows        3
       :draft       @(subscribe [:namepicker.ui/draft :new-students])
       :on-change   #(dispatch [:namepicker.ui/edit-draft :new-students %])
       :on-commit   #(dispatch [:namepicker.ui/commit-new-students class-id])}]
     [:button {:type "submit"} "Add student(s)"]]))

(rf/reg-view picker-panel []
  (let [class @(subscribe [:namepicker/current-class])]
    (if-not class
      [:p.empty-state "Add a class above to get started."]
      (let [class-id (:id class)
            picked   @(subscribe [:namepicker/last-picked-student class-id])
            students @(subscribe [:namepicker/current-students])]
        [:div.picker-panel
         [:h2 (:name class)]
         [add-student-form]
         [student-list]
         [:div.pick-controls
          [:button.pick-button
           {:disabled (empty? students)
            :on-click #(dispatch [:namepicker/pick class-id])}
           "Pick a name!"]
          [:button.reset-button
           {:disabled (empty? students)
            :on-click #(dispatch [:namepicker/reset-picks class-id])}
           "Reset picks"]]
         (when picked
           [:p.picked-display "Picked: " [:strong (:name picked)]])]))))

(rf/reg-view app []
  [:div.namepicker
   [:a.back-home {:href "https://learningnow.com.au/"} "← Learning Now"]
   [:h1 "Name Picker"]
   [class-tabs]
   [add-class-form]
   [picker-panel]])
