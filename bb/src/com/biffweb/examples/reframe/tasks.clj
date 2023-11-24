(ns com.biffweb.examples.reframe.tasks
  (:require [babashka.curl :as curl]
            [babashka.fs :as fs]
            [babashka.process :as process]
            [babashka.tasks :as tasks :refer [clojure]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.stacktrace :as st]
            [com.biffweb.tasks :as biff-tasks]))

(defn dev
  "Starts the app locally.

  After running, wait for the `System started` message. Connect your editor to
  nrepl port 7888. Whenever you save a file, Biff will:

  - Evaluate any changed Clojure files
  - Regenerate static HTML and CSS files
  - Run tests"
  [& args]
  (io/make-parents "target/resources/_")
  (when (fs/exists? "package.json")
    (tasks/shell "npm" "install"))
  (biff-tasks/future-verbose (biff-tasks/css "--watch"))
  (biff-tasks/future-verbose (biff-tasks/shell "npx shadow-cljs watch app"))
  (spit ".nrepl-port" "7888")
  (apply clojure {:extra-env (merge (biff-tasks/secrets) {"BIFF_ENV" "dev"})}
         (concat args (biff-tasks/run-args))))

(def cljs-output "target/resources/public/cljs/app.js")

(defn- push-files-rsync []
  (let [{:biff.tasks/keys [server]} @biff-tasks/config
        files (->> (:out (sh/sh "git" "ls-files"))
                   str/split-lines
                   (map #(str/replace % #"/.*" ""))
                   distinct
                   (concat ["config.edn"
                            "secrets.env"
                            biff-tasks/css-output
                            cljs-output])
                   (filter fs/exists?))]
    (when-not (biff-tasks/windows?)
      (fs/set-posix-file-permissions "config.edn" "rw-------")
      (when (fs/exists? "secrets.env")
        (fs/set-posix-file-permissions "secrets.env" "rw-------")))
    (->> (concat ["rsync" "--archive" "--verbose" "--relative" "--include='**.gitignore'"
                  "--exclude='/.git'" "--filter=:- .gitignore" "--delete-after"]
                 files
                 [(str "app@" server ":")])
         (apply biff-tasks/shell))))

(defn- push-files-git []
  (let [{:biff.tasks/keys [server deploy-to deploy-from deploy-cmd]} @biff-tasks/config]
    (apply biff-tasks/shell (concat ["scp" "config.edn"]
                                    (when (fs/exists? "secrets.env") ["secrets.env"])
                                    [(str "app@" server ":")]))
    (when (fs/exists? biff-tasks/css-output)
      (biff-tasks/shell "ssh" (str "app@" server) "mkdir" "-p"
                        "target/resources/public/css/"
                        "target/resources/public/cljs/")
      (biff-tasks/shell "scp" biff-tasks/css-output (str "app@" server ":" biff-tasks/css-output))
      (biff-tasks/shell "scp" cljs-output (str "app@" server ":" cljs-output)))
    (time (if deploy-cmd
            (apply biff-tasks/shell deploy-cmd)
            ;; For backwards compatibility
            (biff-tasks/shell "git" "push" deploy-to deploy-from)))))

(defn- push-files []
  (if (fs/which "rsync")
    (push-files-rsync)
    (push-files-git)))

(defn soft-deploy
  "Pushes code to the server and evaluates changed files.

  Uploads config and code to the server (see `deploy`), then `eval`s any
  changed files and regenerates HTML and CSS files. Does not refresh or
  restart."
  []
  (biff-tasks/with-ssh-agent
    (let [{:biff.tasks/keys [soft-deploy-fn on-soft-deploy]} @biff-tasks/config]
      (biff-tasks/css "--minify")
      (biff-tasks/shell "npx shadow-cljs release app")
      (push-files)
      (biff-tasks/trench
       (or on-soft-deploy
           ;; backwards compatibility
           (str "\"(" soft-deploy-fn " @com.biffweb/system)\""))))))

(defn deploy
  "Pushes code to the server and restarts the app.

  Uploads config (config.edn and secrets.env) and code to the server, using
  `rsync` if it's available, and `git push` by default otherwise. Then restarts
  the app.

  You must set up a server first. See https://biffweb.com/docs/reference/production/"
  []
  (biff-tasks/with-ssh-agent
   (biff-tasks/css "--minify")
   (biff-tasks/shell "npx shadow-cljs release app")
   (push-files)
   (biff-tasks/restart)))
