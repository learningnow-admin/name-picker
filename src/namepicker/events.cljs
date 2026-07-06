(ns namepicker.events
  "The only place app-db ever gets written — plus the one effect that
  touches the outside world (localStorage) and the weighted-pick logic.

  `reg-event`: one pure handler per state change. `reg-fx`:
  `:namepicker.storage/save` performs the localStorage write the handlers
  only *describe* as data. See docs/core/glossary.md (event, effect)."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [namepicker.db :as db]))

(defn- allocate-next-id [items]
  ((fnil inc 0) (last (keys items))))

(defn- class->storable [{:keys [id name students]}]
  {:id id :name name :students (mapv #(select-keys % [:id :name :picks]) (vals students))})

(rf/reg-fx :namepicker.storage/save
  {:doc       "Write classes + current-class-id out to localStorage. This is
               the one spot that actually touches storage; the handlers just
               ask for it via `persist-db`."
   :platforms #{:client}}
  (fn fx-namepicker-storage-save [_ {:keys [classes current-class-id]}]
    (when-let [ls (.-localStorage js/globalThis)]
      (->> {:classes (mapv class->storable (vals classes))
            :current-class-id current-class-id}
           (clj->js)
           (js/JSON.stringify)
           (.setItem ls db/ls-key)))))

(defn- persist-db [next-db]
  {:db next-db
   :fx [[:namepicker.storage/save (select-keys next-db [:classes :current-class-id])]]})

(rf/reg-event :namepicker/initialise
  {:rf.cofx/requires [:namepicker.storage/state]}
  (fn [{:namepicker.storage/keys [state]} _event]
    {:db (or state db/default-db)}))

(rf/reg-event :namepicker/add-class
  (fn [{:keys [db]} [_ name]]
    (let [name' (str/trim (or name ""))]
      (if (str/blank? name')
        {}
        (let [id (allocate-next-id (:classes db))]
          (persist-db
            (-> db
                (assoc-in [:classes id] {:id id :name name' :students (sorted-map)})
                (assoc :current-class-id id))))))))

(rf/reg-event :namepicker/remove-class
  (fn [{:keys [db]} [_ class-id]]
    (let [classes' (dissoc (:classes db) class-id)
          current  (:current-class-id db)
          current' (if (= current class-id) (first (keys classes')) current)]
      (persist-db (assoc db :classes classes' :current-class-id current')))))

(rf/reg-event :namepicker/select-class
  (fn [{:keys [db]} [_ class-id]]
    (persist-db (assoc db :current-class-id class-id))))

;; Accepts a batch of names (e.g. one textarea's worth, newline/comma
;; separated) so a teacher can paste a whole roster in one submit instead
;; of adding students one at a time.
(defn- add-students [db class-id names]
  (reduce
    (fn [db name]
      (let [id (allocate-next-id (get-in db [:classes class-id :students]))]
        (assoc-in db [:classes class-id :students id] {:id id :name name :picks 0})))
    db
    (->> names (map str/trim) (remove str/blank?))))

(rf/reg-event :namepicker/add-students
  (fn [{:keys [db]} [_ class-id names]]
    (let [next-db (add-students db class-id names)]
      (if (= next-db db)
        {}
        (persist-db next-db)))))

(rf/reg-event :namepicker/remove-student
  (fn [{:keys [db]} [_ class-id student-id]]
    (persist-db (update-in db [:classes class-id :students] dissoc student-id))))

;; Weighted pick: a student's chance of being picked is inversely
;; proportional to how many times they've already been picked, so the
;; roster stays fair over many draws without a hard-reset "bag".
(defn- weighted-pick-id [students]
  (let [weighted (mapv (fn [s] [(:id s) (/ 1.0 (inc (:picks s)))]) students)
        total    (reduce + (map second weighted))
        target   (* (rand) total)]
    (loop [[[id w] & more] weighted
           acc 0.0]
      (let [acc' (+ acc w)]
        (if (or (empty? more) (< target acc'))
          id
          (recur more acc'))))))

(rf/reg-event :namepicker/pick
  (fn [{:keys [db]} [_ class-id]]
    (let [students (vals (get-in db [:classes class-id :students]))]
      (if (empty? students)
        {}
        (let [picked-id (weighted-pick-id students)]
          (persist-db
            (-> db
                (update-in [:classes class-id :students picked-id :picks] inc)
                (assoc :last-picked {:class-id class-id :student-id picked-id}))))))))

(rf/reg-event :namepicker/reset-picks
  (fn [{:keys [db]} [_ class-id]]
    (persist-db
      (update-in db [:classes class-id :students]
                 (fn [students]
                   (into (sorted-map)
                         (map (fn [[id s]] [id (assoc s :picks 0)]))
                         students))))))

;; ---- UI / form drafts ------------------------------------------------------
;; Pure UI froth, never persisted — same rule as todomvc's :ui slice.

(rf/reg-event :namepicker.ui/edit-draft
  (fn [{:keys [db]} [_ which value]]
    {:db (assoc-in db [:ui :drafts which] value)}))

(rf/reg-event :namepicker.ui/commit-new-class
  (fn [{:keys [db]} _event]
    (let [name (get-in db [:ui :drafts :new-class])]
      {:db (assoc-in db [:ui :drafts :new-class] "")
       :fx [[:dispatch [:namepicker/add-class name]]]})))

(rf/reg-event :namepicker.ui/commit-new-students
  (fn [{:keys [db]} [_ class-id]]
    (let [text (get-in db [:ui :drafts :new-students])]
      {:db (assoc-in db [:ui :drafts :new-students] "")
       :fx [[:dispatch [:namepicker/add-students class-id (str/split text #"[\n,]+")]]]})))
