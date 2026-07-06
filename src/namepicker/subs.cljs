(ns namepicker.subs
  "The derivation graph the views read from. Layered like todomvc's:
  `:namepicker/classes` reads app-db directly, everything else stacks on
  top of it or on the route-free equivalent, `:namepicker/current-class-id`."
  (:require [re-frame.core :as rf]))

(rf/reg-sub :namepicker/classes
  (fn [db _] (:classes db)))

(rf/reg-sub :namepicker/class-list
  :<- [:namepicker/classes]
  (fn [classes _] (vals classes)))

(rf/reg-sub :namepicker/current-class-id
  (fn [db _] (:current-class-id db)))

(rf/reg-sub :namepicker/current-class
  :<- [:namepicker/classes]
  :<- [:namepicker/current-class-id]
  (fn [[classes current-id] _]
    (get classes current-id)))

(rf/reg-sub :namepicker/current-students
  :<- [:namepicker/current-class]
  (fn [class _]
    (vals (:students class))))

(rf/reg-sub :namepicker/last-picked
  (fn [db _] (:last-picked db)))

;; `class-id` is passed as a query arg so a row only re-renders when the
;; pick actually belongs to the class it's showing.
(rf/reg-sub :namepicker/last-picked-student
  :<- [:namepicker/last-picked]
  :<- [:namepicker/classes]
  (fn [[last-picked classes] [_ class-id]]
    (when (and last-picked (= class-id (:class-id last-picked)))
      (get-in classes [class-id :students (:student-id last-picked)]))))

(rf/reg-sub :namepicker.ui/draft
  (fn [db [_ which]] (get-in db [:ui :drafts which])))
