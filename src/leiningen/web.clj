(ns leiningen.web
  (:require [lein-webutils.plugin :refer :all]
            [clojure.pprint :refer [pprint]]))

(defn docompile [project & args]
  (if (empty? args)
    (run-all-compilers project)
    (run-compiler project (first args))))

(defn dowatch [project]
  (start-watch project)
  (println "Press enter to exit.")
  (.read System/in))

(defn web [project subtask & args]
  (let [task-fun (case subtask
                   "compile" docompile
                   "watch" dowatch)]
    (apply task-fun project args)))
