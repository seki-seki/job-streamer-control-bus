(ns job-streamer.control-bus.component.scheduler-test
  (:require (job-streamer.control-bus.component [scheduler :as scheduler]
                                                [apps :as apps]
                                                [datomic :refer [datomic-component] :as d]
                                                [migration :refer [migration-component]])
            (job-streamer.control-bus [system :as system]
                                      [model :as model]
                                      [config :as config])
            [com.stuartsierra.component :as component]
            [meta-merge.core :refer [meta-merge]]
            [clojure.test :refer :all]
            [clojure.pprint :refer :all]))

(def test-config
  {:datomic {:recreate? true
             :uri "datomic:mem://test"}})

(def config
  (meta-merge config/defaults
              config/environ
              test-config))

(defn new-system [config]
  (-> (component/system-map
       :apps      (apps/apps-component (:apps config))
       :scheduler (scheduler/scheduler-component (:scheduler config))
       :datomic   (datomic-component   (:datomic config))
       :migration (migration-component {:dbschema model/dbschema}))
      (component/system-using
       {:apps [:datomic]
        :migration [:datomic]
        :scheduler [:datomic]})
      (component/start-system)))

(deftest hh:MM?
  (testing "nil"
    (is (not (scheduler/hh:MM? nil))))
  (testing "valid"
    (is (scheduler/hh:MM? "00:00"))
    (is (scheduler/hh:MM? "23:59")))
  (testing "invalid-hour"
    (is (not (scheduler/hh:MM? "-01:01")))
    (is (not (scheduler/hh:MM? "24:01"))))
  (testing "invalid-minutes"
    (is (not (scheduler/hh:MM? "01:-01")))
    (is (not (scheduler/hh:MM? "01:60"))))
  (testing "invalid-format"
    (is (not (scheduler/hh:MM? "0101")))
    (is (not (scheduler/hh:MM? "a")))))

(deftest to-ms-from-hh:MM
  (testing "nil"
    (is (= 0 (scheduler/to-ms-from-hh:MM nil))))
  (testing "empty"
    (is (= 0 (scheduler/to-ms-from-hh:MM ""))))
  (testing "nomal"
    (is (= 3660000 (scheduler/to-ms-from-hh:MM "01:01"))))
  (testing "invalid"
    (is (= 0 (scheduler/to-ms-from-hh:MM "this is invalid"))))
  ;This function does not have Responsibility of validation
  )

(deftest entry-resource
  (let [system (new-system config)
        handler (-> (scheduler/entry-resource (:scheduler system) "test-job"))]
    (testing "change scheduler is not authorized"
      (let [request {:request-method :post
                     :identity {:permissions #{:permission/read-job :permission/update-job :permission/create-job :permission/delete-job}}
                     :content-type "application/edn"}]
        (is (= 403 (-> (handler request) :status)))))
    (testing "operate scheduler is not authorized"
      (let [request {:request-method :put
                     :identity {:permissions #{:permission/read-job :permission/update-job :permission/create-job :permission/delete-job}}
                     :content-type "application/edn"}]
        (is (= 403 (-> (handler request) :status)))))
    (testing "delete scheduler is not authorized"
      (let [request {:request-method :delete
                     :identity {:permissions #{:permission/read-job :permission/update-job :permission/create-job :permission/delete-job}}
                     :content-type "application/edn"}]
        (is (= 403 (-> (handler request) :status)))))))
