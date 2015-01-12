(ns lein-webutils.plugin
  (:require [leiningen.ring.server]
            [robert.hooke :refer [add-hook]]
            [clojure-watch.core :as watch]
            [clojure.java.io :as io]
            [lein-webutils.compilers :as compilers]
            [lein-webutils.fswatch :as fswatch])
  (:import java.nio.file.Path))

(defn- resource-dir
  "Returns the resource directory used for compiled output."
  [project]
  (io/file (:target-path project) "webutils-resources"))

(defn- public-dir
  "The output directory under the resource directory."
  [project]
  (io/file (resource-dir project) "public"))

(defn- replace-ext
  "Replaces the file extension of the given file."
  [file new-ext]
  (let [str-file (str file)
        dot-index (.lastIndexOf str-file (int \.))
        base (subs str-file 0 dot-index)]
    (io/file (str base \. new-ext))))

(defn- absolutize
  "Makes path absolute to root if it is relative."
  [root path]
  (if (.isAbsolute (io/file path))
    path
    (io/file root path)))

(defn- relativize
  "Returns the path of child relative to parent."
  [parent child]
  (-> parent .toPath (.relativize (.toPath child)) .toFile))

(defn- source-paths [project]
  "Absolute source paths."
  (->> project :web :source-paths
       (map #(absolutize (:root project) %))))

(defn- web-sources
  "All files in all source directories."
  [project]
  (for [dir (source-paths project)
        file (file-seq dir)]
    [dir file]))

(defn- compile-file
  "Compiles a single file with the given spec."
  [project spec src-dir file]
  (let [dest-file (replace-ext (io/file (public-dir project)
                                        (relativize src-dir file))
                               (:target-ext spec))]
    (-> dest-file .getParentFile .mkdirs)
    ((:func spec) project file dest-file)))

(defn- sources-for-spec
  "The sources for a certain spec."
  [project spec]
  (for [[dir file :as pair] (web-sources project)
        :when (re-find (:match spec) (.getName file))]
    pair))

(defn- run-compile-spec
  "Runs a single compiler spec."
  [project spec]
  (doseq [[src-dir file] (sources-for-spec project spec)]
    (compile-file project spec src-dir file)))

(defn run-all-compilers
  "Runs all compilers."
  [project]
  (doseq [[id spec] (compilers/get-compilers)]
    (run-compile-spec project spec)))

(defn run-compiler
  "Runs a single compiler by name."
  [project compiler]
  (if-let [spec ((compilers/get-compilers) compiler)]
    (run-compile-spec project spec)
    (throw (ex-info (format "No such compiler '%s'" compiler)
                    {:exit-code 1}))))

(def ignore-watch-regex #"^\.#")

(defn- on-file-event
  "Called when a file changes."
  [project filename]
  (doseq [[id spec] (compilers/get-compilers)
          :let [name (-> filename .getName str)]
          :when (re-find (:watch spec) name)
          :when (not (re-find ignore-watch-regex name))]
    (println (format "%s changed" filename))
    (run-compile-spec project spec)))

(defn start-watch
  "Starts the file watcher."
  [project]
  (run-all-compilers project)
  (println "Watching dirs:")
  (let [dirs (source-paths project)]
    (doseq [dir dirs] (println (format "   %s" dir)))
    (fswatch/watch-dirs dirs #(on-file-event project %))))

(defn- watch-hook
  "Hook to start watching stuff."
  [f project & args]
  (start-watch project)
  (apply f project args))

(defn hooks []
  (add-hook #'leiningen.ring.server/server #'watch-hook))

(defn middleware [project]
  (update-in project [:resource-paths] conj (-> project resource-dir str)))
