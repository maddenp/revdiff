;; TODO add a debug toggle and some related verbose prints?
;; TODO maybe print log message / committer info (could use awt/swing popup)?
;; TODO pass all '--' options (e.g. --username) to svn?
;; TODO add command-line switch for case insensitivity!
;; TODO make get-revpairs non-recursive?

(ns revdiff.core
  (:gen-class)
  (:import [java.io.StringBufferInputStream])
  (:require [clojure.tools.cli :as cli])
  (:use [clojure.xml :only [parse]])
  (:use [clojure.java.shell :only [sh]])
  (:use [clojure.string :only [split split-lines trim]]))

(declare matching-files log svndiff uri? vpaths)

;; Suppress svn user prompts.

(def shhh "--non-interactive")

;; Strip peg revision, if present.

(defn baseobject [object]
  (clojure.string/replace object #"@[0-9]+$" ""))

;; Command-line interface options.

(def cliopts
  [["-h" "--help" "Show usage information"]
   ["-i" "--case-insensitive" "Treat regexp as case-insensitive"]
   ["-d" "--diff-opts options" "Quoted list of options to pass to svn diff"]
   ["-l" "--log-opts options" "Quoted list of options to pass to svn log"]])

;; A string containing the output of "diff" on the two svn revision of object.

(defn diff [r1 r2 object]
  (let [object (baseobject object)
        v (vpaths r1 r2 object object)]
    (sh "svn" "diff" "--diff-cmd" "diff" "-x" "-u0" shhh (first v) (second v))))

;; Given a list of revision pairs, diff those for which the given filter term
;; (if any) is present on a changed line in that pair. If no filter term is
;; given, diff all changed files from all revision pairs.

(defn diffrevpairs [object filt revpairs]
  (when (seq revpairs)
    (let [r1 (first revpairs)
          r2 (first (rest revpairs))
          mf (matching-files r1 r2 object filt)]
      (println (str "Checking: r" r1 " vs r" r2))
      (doseq [filename mf] (svndiff r1 r2 filename object))
      (diffrevpairs object filt (rest (rest revpairs))))))

;; Try to explain why this has failed.

(defn errmsg []
  (println "\nError retrieving repository information. Perhaps:\n")
  (println "- The supplied filename or URI is invalid, or")
  (println "- Your valid svn authentication credentials are not cached.")
  (println))

;; Return a newest-first sequence of revision numbers in which the object
;; changed.

(defn get-revlist [opts object]
  (let [logstream (java.io.ByteArrayInputStream. (.getBytes (log opts object)))]
    (for [e (try
              (xml-seq (parse logstream))
              (catch Exception e (errmsg)))
          :when (seq (:attrs e))]
    (:revision (:attrs e)))))

;; Return a sequence of revision pairs for potential comparison -- e.g. for the
;; revlist (9 8 6 4 1), return (8 9 6 8 4 6 1 4).

(defn get-revpairs [revlist object]
  (when (seq (rest revlist))
    (concat (list (first (rest revlist)) (first revlist))
            (get-revpairs (rest revlist) object))))

;; Return a string containing the xml-formatted svn log for the requested
;; object.

(defn log [opts object]
  (print "Fetching log... ")
  (flush)
  (let [log (:out (sh "svn" "log" opts shhh "--xml" object))]
    (println)
    log))

;; Obtain a diff from svn for the given object between the specified revisions.
;; Break each diff into blocks (delineated by the text "Index: "), where each
;; block describes one file. The block's first line is the filename, and lines
;; beginning with a single '+' or '-' are the changes. Construct and return a
;; vector of the names of files where the filter term appears on a changed line.
;; If no filter term is given, a changed file automatically matches.

(defn matching-files [r1 r2 object filt]
  (let [blocks (rest (split (:out (diff r1 r2 object)) #"^Index: |\nIndex: "))]
    (remove nil?
            (into []
                  (for [block blocks]
                    (let [matching-line-in? #(re-matches (re-pattern filt) %)
                          diff-line? #(re-matches #"^[+-][^+-].*" %)
                          strip+- #(clojure.string/replace % #"^[+-]" "")
                          all-lines (split-lines block)
                          diff-lines (filter diff-line? all-lines)
                          changes (map strip+- diff-lines)
                          filename (trim (first all-lines))]
                      (if (some matching-line-in? changes) filename)))))))

;; NEED NEW COMMENT HERE

(defn svndiff [r1 r2 filename object]
  (let [object (baseobject object)
        r (re-pattern (str "^.*/" filename "$"))
        p (if (uri? object)
            (if (re-matches r object) object (str object "/" filename))
            filename)
        v (vpaths r1 r2 p object)]
    (sh "svn" "diff" (first v) (second v))))

;; Is path a uri? If it contains :// assume it is.

(defn uri? [path] (re-matches #".*://.*" path))

;; Print usage information.

(defn usage [summary code]
  (println (str "\nUsage: revdiff [options] object [regexp]\n\n  Options:\n"))
  (println (str summary "\n"))
  (println "  object: svn URI or name of versioned object in working-copy")
  (println "  regexp: only show diffs where a changed line matches regexp\n")
  (println "  See https://github.com/maddenp/revdiff for notes on options.\n")
  (System/exit code))

;; If object is a uri, the "-rn:m" revision-range format will not work if object
;; is no longer present in the head revision, in which case we have to use the
;; "object@n object@m" revision-specification format. Return the correctly
;; formatted versioned-path(s) string for use by a diff command.

(defn vpaths [r1 r2 path object]
  (if (uri? object)
    (list (str path "@" r1) (str path "@" r2))
    (list (str "-r" r1 ":" r2) path)))

;; Entry point from command line.

(defn -main [& args]
  (let [{:keys [options arguments summary]} (cli/parse-opts args cliopts)
        object (first arguments)
        filt (or (second arguments) ".*")
        optsd (:diff-opts options)
        optsl (:log-opts options)]
    (if (:help options) (usage summary 0))
    (if (not object)
      (usage summary 1)
      (let [revpairs (get-revpairs (get-revlist optsl object) object)]
        (diffrevpairs object filt revpairs)
        (shutdown-agents)))))
