(ns revdiff.core
  (:gen-class)
  (:import [java.io.StringBufferInputStream])
  (:use [clojure.xml :only [parse]])
  (:use [clojure.java.shell :only [sh]])
  (:use [clojure.string :only [split split-lines trim]]))

;; TODO add a debug toggle and some related verbose prints?
;; TODO is --stop-on-copy on log really a good idea?
;; TODO maybe print log message / committer info (could use awt/swing popup)?
;; TODO pass all '--' options (e.g. --username) to svn?
;; TODO item, term used to be global: refactor to avoid passing them so much!
;; TODO add command-line switch for case insensitivity!

(def shhh "--non-interactive")  ; suppress svn user prompts

;; A string containing the xml-formatted svn log for the requested item. Stop on
;; copy to avoid following the history of a differently-named object.

(defn log [item] (:out (sh "svn" "log" shhh "--stop-on-copy" "--xml" item)))
;;(defn log [item] (slurp "xml"))

;; Try to explain why this has failed.

(defn errmsg []
  (println "\nError retrieving repository information. Perhaps:\n")
  (println "- The supplied filename or URI is invalid, or")
  (println "- Your valid svn authentication credentials are not cached.")
  (println))

;; A newest-first sequence of revision numbers in which the item changed

(defn revlist [item]
  (let [logstream (java.io.ByteArrayInputStream. (.getBytes (log item)))]
    (for [e (try
              (xml-seq (parse logstream))
              (catch Exception e (errmsg)))
          :when (seq (:attrs e))]
    (:revision (:attrs e)))))

;; A sequence of revision pairs for potential comparison -- e.g. for the revlist
;; (9 8 6 4 1), return (8 9 6 8 4 6 1 4).

(defn revpairs [revlist item]
  (when (seq (rest revlist))
    (concat (list (first (rest revlist)) (first revlist))
            (revpairs (rest revlist) item))))

;; Is path a uri? If it contains :// assume it is.

(defn uri? [path] (re-matches #".*://.*" path))

;; If item is a uri, the "-rn:m" revision-range format will not work if item is
;; no longer present in the head revision, in which case we have to use the
;; "item@n item@m" revision-specification format. Return the correctly formatted
;; versioned-path(s) string for use by a diff command.

(defn vpaths [r1 r2 path item]
  (if (uri? item)
    (list (str path "@" r1) (str path "@" r2))
    (list (str "-r" r1 ":" r2) path)))
  
;; A string containing the output of "diff" on the two svn revision of item.

(defn diff [r1 r2 item]
  (let [v (vpaths r1 r2 item item)]
    (sh "svn" "diff" "--diff-cmd" "diff" "-x" "-u0" shhh (first v) (second v))))

;; Obtain a diff from svn for the given item between the specified revisions.
;; Break each diff into blocks (delineated by the text "Index: "), where each
;; block describes one file. The block's first line is the filename, and lines
;; beginning with a single '+' or '-' are the changes. Construct and return a
;; vector of the names of files where the filter term appears on a changed line.
;; If no filter term is given, a changed file automatically matches.

(defn matching-files [r1 r2 item term]
  (let [blocks (rest (split (:out (diff r1 r2 item)) #"^Index: |\nIndex: "))]
    (remove nil?
            (into []
                  (for [block blocks]
                    (let [lines (split-lines block)
                          filename (trim (first lines))
                          changes (filter #(re-matches #"^[+-][^+-].*" %) lines)
                          re (re-pattern (str ".*" term ".*"))
                          matching-line-in #(re-matches re %)]
                      (if (some matching-line-in changes) filename)))))))

;; Run "svn diff" on the file/pathname in index. The format of the file/path
;; name following "Index:" in diff's output depends on the argument: If the
;; argument is a filesystem pathname, the index name is absolute, and we can
;; simply use that. If the argument is a uri, the index name is relative to the
;; base item name. If the base item name was the uri to a file (and therefore a
;; complete pathname) we can use item; if it was the uri to a directory, we have
;; to concatenate the item and index to form a complete pathname.

(defn svndiff [r1 r2 index item]
  (let [r (re-pattern (str "^.*/" index "$"))
        p (if (uri? item)
            (if (re-matches r item) item (str item "/" index))
            index)
        v (vpaths r1 r2 p item)]
    (sh "svn" "diff" (first v) (second v))))

;; Given a list of revision pairs, diff those for which the given filter term (if
;; any) is present on a changed line in that pair. If no filter term is given,
;; diff all changed files from all revision pairs.

(defn diffrevpairs [item term revpairs]
  (when (seq revpairs)
    (let [r1 (first revpairs)
          r2 (first (rest revpairs))
          mf (matching-files r1 r2 item term)]
      (println (str "Checking: r" r1 " vs r" r2))
      (doseq [index mf] (svndiff r1 r2 index item))
      (diffrevpairs item term (rest (rest revpairs))))))

(defn usage []
  (println (str "\nUsage: revdiff item [term]\n"))
  (println "  item : svn uri or name of versioned working-copy object")
  (println "  term : only show diffs with term present on a changed line\n"))

(defn -main [& args]
  (let [item (first args) term (second args)]
    (if (not item)
      (usage)
      (diffrevpairs item term (revpairs (revlist item) item)))))
