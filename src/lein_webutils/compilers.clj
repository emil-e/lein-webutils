(ns lein-webutils.compilers
  (:require [clojure.java.shell :refer [sh]]
            [leiningen.core.eval :as lein]
            [clojure.java.io :as io]
            [clojure.string :refer [trim]]))

(defn- npm-install []
  (sh "npm" "install"))

(defn- npm-bin [bin]
  (npm-install)
  (-> (sh "npm" "bin") :out trim (io/file bin) str))

(defn- browserify-opts [project]
  (let [{:keys [transforms options]} (-> project :web :browserify)
        transform-opts (for [transform transforms]
                         (concat ["-t" "["] transform ["]"]))]
    (concat options (apply concat transform-opts))))

(defn- browserify [project in out]
  (println (format "Browserify %s..." (.getName in)))
  (let [cmd (concat [(npm-bin "browserify")]
                    (browserify-opts project)
                    [(str in) "-o" (str out)])
        result (apply lein/sh cmd)]
    (when (not= result 0)
      (throw (ex-info "Browserify failed"
                      {:exit-code 1})))
    result))

(defn- stylus-opts [project]
  (-> project :web :stylus :options))

(defn- stylus [project in out]
  (println (format "Stylus %s..." (.getName in)))
  (let [dir (-> in .getParentFile str)
        cmd (concat [(npm-bin "stylus") "-I" dir]
                    (stylus-opts project))
        result (apply sh (concat cmd [:in in]))]
    (if (= (:exit result) 0)
      (spit out (:out result))
      (do
        (println (:err result))
        (throw (ex-info "Stylus failed"
                        {:exit-code 1}))))))

(def ^{:private true} compiler-map
  {"browserify" {:match #"^main\.jsx?$"
                 :watch #"\.jsx?$"
                 :target-ext "js"
                 :func browserify}

   "stylus"     {:match #"^main\.styl$"
                 :watch #"\.(css|styl)$"
                 :target-ext "css"
                 :func stylus}})

(defn get-compilers [] compiler-map)
