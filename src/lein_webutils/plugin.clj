(ns lein-webutils.plugin
  (:require [leiningen.ring.server]
            [robert.hooke :refer [add-hook]]
            [clojure-watch.core :as watch]
            [clojure.java.io :as io]
            [lein-webutils.compilers :as compilers]
            [lein-webutils.fswatch :as fswatch])
  (:import java.nio.file.Path))

(defn- resource-dir [project]
  "The base resource directory."
  (io/file (:target-path project) "webutils-resources"))

(defn- public-dir [project]
  "The output directory under the resource directory."
  (io/file (resource-dir project) "public"))

(defn- replace-ext [file new-ext]
  (let [str-file (str file)
        dot-index (.lastIndexOf str-file (int \.))
        base (subs str-file 0 dot-index)]
    (io/file (str base \. new-ext))))

(defn- absolutize [root path]
  (if (.isAbsolute (io/file path))
    path
    (io/file root path)))

(defn- relativize [parent child]
  (-> parent .toPath (.relativize (.toPath child)) .toFile))

(defn- source-paths [project]
  (->> project :web :source-paths
       (map #(absolutize (:root project) %))))

(defn- web-sources [project]
  (for [dir (source-paths project)
        file (file-seq dir)]
    [dir file]))

(defn- compile-file [project spec src-dir file]
  (let [dest-file (replace-ext (io/file (public-dir project)
                                        (relativize src-dir file))
                               (:target-ext spec))]
    (-> dest-file .getParentFile .mkdirs)
    ((:func spec) project file dest-file)))

(defn- sources-for-spec [project spec]
  (for [[dir file :as pair] (web-sources project)
        :when (re-find (:match spec) (.getName file))]
    pair))

(defn- run-compile-spec [project spec]
  (doseq [[src-dir file] (sources-for-spec project spec)]
    (compile-file project spec src-dir file)))

(defn run-all-compilers [project]
  (doseq [[id spec] (compilers/get-compilers)]
    (run-compile-spec project spec)))

(defn run-compiler [project compiler]
  (if-let [spec ((compilers/get-compilers) compiler)]
    (run-compile-spec project spec)
    (throw (ex-info (format "No such compiler '%s'" compiler)
                    {:exit-code 1}))))

(def ignore-watch-regex #"^\.#")

(defn- on-file-event [project filename]
  (doseq [[id spec] (compilers/get-compilers)
          :let [name (-> filename .getName str)]
          :when (re-find (:watch spec) name)
          :when (not (re-find ignore-watch-regex name))]
    (println (format "%s changed" filename))
    (run-compile-spec project spec)))

(defn start-watch [project]
  (println "Watching dirs:")
  (let [dirs (source-paths project)]
    (doseq [dir dirs] (println (format "   %s" dir)))
    (fswatch/watch-dirs dirs #(on-file-event project %))))

(defn- watch-hook [f project & args]
  (start-watch project)
  (apply f project args))

(defn hooks []
  (add-hook #'leiningen.ring.server/server #'watch-hook))

(defn middleware [project]
  (update-in project [:resource-paths] conj (resource-dir project)))
