(ns lein-webutils.fswatch
  (:require [clojure.java.io :refer [reader file]]
            [clojure.string :refer [trim]]))

(defn- watch-process [process handler]
  (doseq [line (-> process .getInputStream reader line-seq)]
    (handler (-> line trim file))))

(defn watch-dirs [dirs handler]
  "Watches the given directories for file changes"
  (let [cmd (concat ["fswatch" "-l" "0.5" "-r"] (map str dirs))
        process (.. Runtime (getRuntime) (exec (into-array cmd)))]
    (.start (Thread. #(watch-process process handler)))
    (fn [] (.destroy process))))
