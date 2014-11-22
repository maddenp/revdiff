;; TODO add a debug toggle and some related verbose prints?
;; TODO is --stop-on-copy on log really a good idea?
;; TODO maybe print log message / committer info (could use awt/swing popup)?
;; TODO pass all '--' options (e.g. --username) to svn?
;; TODO add command-line switch for case insensitivity!

(ns revdiff.core
  (:gen-class)
  (:import [java.io.StringBufferInputStream])
  (:use [clojure.xml :only [parse]])
  (:use [clojure.java.shell :only [sh]])
  (:use [clojure.string :only [split split-lines trim]]))

(declare matching-files svndiff uri? vpaths)

;; Suppress svn user prompts.

(def shhh "--non-interactive")

;; Strip peg revision, if present.

(defn baseitem [item]
  (clojure.string/replace item #"@[0-9]+$" ""))

;; A string containing the output of "diff" on the two svn revision of item.

(defn diff [r1 r2 item]
  (let [item (baseitem item)
        v (vpaths r1 r2 item item)]
    (sh "svn" "diff" "--diff-cmd" "diff" "-x" "-u0" shhh (first v) (second v))))

;; Given a list of revision pairs, diff those for which the given filter term
;; (if any) is present on a changed line in that pair. If no filter term is
;; given, diff all changed files from all revision pairs.

(defn diffrevpairs [item filt revpairs]
  (when (seq revpairs)
    (let [r1 (first revpairs)
          r2 (first (rest revpairs))
          mf (matching-files r1 r2 item filt)]
      (println (str "Checking: r" r1 " vs r" r2))
      (doseq [filename mf] (svndiff r1 r2 filename item))
      (diffrevpairs item filt (rest (rest revpairs))))))

;; Try to explain why this has failed.

(defn errmsg []
  (println "\nError retrieving repository information. Perhaps:\n")
  (println "- The supplied filename or URI is invalid, or")
  (println "- Your valid svn authentication credentials are not cached.")
  (println))

;; Return a string containing the xml-formatted svn log for the requested item.

(defn log [item]
  (:out (sh "svn" "log" shhh "--stop-on-copy" "--xml" item)))

;; Obtain a diff from svn for the given item between the specified revisions.
;; Break each diff into blocks (delineated by the text "Index: "), where each
;; block describes one file. The block's first line is the filename, and lines
;; beginning with a single '+' or '-' are the changes. Construct and return a
;; vector of the names of files where the filter term appears on a changed line.
;; If no filter term is given, a changed file automatically matches.

(defn matching-files [r1 r2 item filt]
  (let [blocks (rest (split (:out (diff r1 r2 item)) #"^Index: |\nIndex: "))]
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

;; Return a newest-first sequence of revision numbers in which the item changed.

(defn revlist [item]
  (let [logstream (java.io.ByteArrayInputStream. (.getBytes (log item)))]
    (for [e (try
              (xml-seq (parse logstream))
              (catch Exception e (errmsg)))
          :when (seq (:attrs e))]
    (:revision (:attrs e)))))

;; Return a sequence of revision pairs for potential comparison -- e.g. for the
;; revlist (9 8 6 4 1), return (8 9 6 8 4 6 1 4).

(defn revpairs [revlist item]
  (when (seq (rest revlist))
    (concat (list (first (rest revlist)) (first revlist))
            (revpairs (rest revlist) item))))

;; WRITE NEW COMMENT HERE

(defn svndiff [r1 r2 filename item]
  (let [item (baseitem item)
        r (re-pattern (str "^.*/" filename "$"))
        p (if (uri? item)
            (if (re-matches r item) item (str item "/" filename))
            filename)
        v (vpaths r1 r2 p item)]
    (sh "svn" "diff" (first v) (second v))))

;; Is path a uri? If it contains :// assume it is.

(defn uri? [path] (re-matches #".*://.*" path))

;; Print usage information.

(defn usage []
  (println (str "\nUsage: revdiff item [regex]\n"))
  (println "  item  : svn uri or name of versioned working-copy object")
  (println "  regex : only show diffs where a changed line matches regex\n"))

;; If item is a uri, the "-rn:m" revision-range format will not work if item is
;; no longer present in the head revision, in which case we have to use the
;; "item@n item@m" revision-specification format. Return the correctly formatted
;; versioned-path(s) string for use by a diff command.

(defn vpaths [r1 r2 path item]
  (if (uri? item)
    (list (str path "@" r1) (str path "@" r2))
    (list (str "-r" r1 ":" r2) path)))

(defn -main [& args]
  (let [item (first args) filt (or (second args) ".*")]
    (if (not item)
      (usage)
      (do
        (diffrevpairs item filt (revpairs (revlist item) item))
        (shutdown-agents)))))
