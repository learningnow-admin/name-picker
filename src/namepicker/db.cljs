(ns namepicker.db
  "The shape of app-db and the boot read that fills it.

  :classes is a sorted-map of class-id -> {:id :name :students}, where
  :students is itself a sorted-map of student-id -> {:id :name :picks}.
  :current-class-id names which class the picker panel shows. :last-picked
  is ephemeral display state (not persisted). The :ui slice holds the two
  controlled-input drafts (new class name, new students bulk text)."
  (:require [re-frame.core :as rf]))

(def ls-key "namepicker-reframe2")

(def ^:private seed-names
  ["Alice" "Bob" "Chandra" "Diego" "Elena" "Farah" "Grace" "Hiro"])

(defn- seed-students []
  (into (sorted-map)
        (map-indexed (fn [i n] [(inc i) {:id (inc i) :name n :picks 0}]))
        seed-names))

(def default-db
  {:classes          (sorted-map 1 {:id 1 :name "Homeroom" :students (seed-students)})
   :current-class-id 1
   :last-picked      nil
   :ui               {:drafts {:new-class "" :new-students ""}}})

(defn- as-int [v fallback]
  (or (try (int v) (catch :default _ nil)) fallback))

(defn- normalise-student [{:keys [id name picks]}]
  (when (some? id)
    {:id (as-int id nil) :name (str name) :picks (as-int picks 0)}))

(defn- normalise-class [{:keys [id name students]}]
  (when (some? id)
    {:id       (as-int id nil)
     :name     (str name)
     :students (into (sorted-map)
                      (comp (map normalise-student)
                            (remove nil?)
                            (map (fn [s] [(:id s) s])))
                      (or students []))}))

;; The saved classes are a fact about the world out there (storage), and
;; `:namepicker/initialise` folds them into durable app-db. Reading the
;; snapshot through a RECORDABLE coeffect (rather than a live localStorage
;; call inside the handler) keeps the durable read replayable. See
;; docs/core/glossary.md#recordable-vs-ambient-coeffects.
(defn- storage->state [raw]
  (when (seq raw)
    (try
      (let [parsed  (js->clj (js/JSON.parse raw) :keywordize-keys true)
            classes (into (sorted-map)
                          (comp (map normalise-class)
                                (remove nil?)
                                (map (fn [c] [(:id c) c])))
                          (:classes parsed))
            current (as-int (:current-class-id parsed) nil)]
        (when (seq classes)
          {:classes          classes
           :current-class-id (if (contains? classes current) current (first (keys classes)))
           :last-picked       nil
           :ui                {:drafts {:new-class "" :new-student ""}}}))
      (catch :default _ nil))))

(defn read-state-from-storage []
  (some-> (.-localStorage js/globalThis)
          (.getItem ls-key)
          (storage->state)))

(rf/reg-cofx :namepicker.storage/state
  {:recordable? true
   :doc "Recordable coeffect: saved classes + current-class-id, read from
         localStorage as a sorted-map tree. The supplier runs at the boot
         dispatch; replay re-folds the recorded snapshot instead of reading
         localStorage again."}
  (fn [] (read-state-from-storage)))
