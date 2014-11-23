(ns revdiff.core
  (:gen-class)
  (:import [java.io.StringBufferInputStream])
  (:require [clojure.tools.cli :as cli])
  (:use [clojure.xml :only [parse]])
  (:use [clojure.java.shell :only [sh]])
  (:use [clojure.string :only [join split split-lines trim]]))

(declare matching-files log squeeze svndiff uri? vpaths)

;; defs

(def shhh "--non-interactive")

(def show-cmd #(println (str "Command: " (squeeze (join " " %)))))

(def squeeze #(clojure.string/replace % #"\s\s+" " "))

;; defns

;; Strip peg revision, if present.

(defn baseobject [object]
  (clojure.string/replace object #"@[0-9]+$" ""))

;; Command-line interface options.

(def cliopts
  [["-h" "--help" "Show usage information"]
   ["-i" "--case-insensitive" "Treat regexp as case-insensitive"]
   ["-d" "--diff-opts options" "Quoted list of options to pass to svn diff"]
   ["-l" "--log-opts options" "Quoted list of options to pass to svn log"]
   ["-s" "--show-cmds" "Show svn commands as they are issued"]])

;; A string containing the output of "diff" on the two svn revision of object.

(defn diff [r1 r2 object show]
  (let [object (baseobject object)
        v (vpaths r1 r2 object object)
        o1 (first v)
        o2 (second v)
        cmd-components ["svn" "diff" "--diff-cmd" "diff" "-x" "-u0" shhh o1 o2]]
    (if show (show-cmd cmd-components))
    (apply sh cmd-components)))

;; Show diffs for files that changed (modulo the optional filtering regexp)
;; between each revision pair in the given list.

(defn diff-revpairs [object filt revpairs show insens]
  (loop [x revpairs]
    (let [r1 (first x)
          r2 (second x)
          mf (matching-files r1 r2 object filt show insens)]
      (println (str "Checking: r" r1 " vs r" r2))
      (doseq [filename mf] (svndiff r1 r2 filename object show))
      (recur (drop 2 x)))))

;; Try to explain why this has failed.

(defn errmsg []
  (println "\nError retrieving repository information. Perhaps:\n")
  (println "- The supplied filename or URI is invalid, or")
  (println "- Your valid svn authentication credentials are not cached.")
  (println))

;; Return a newest-first sequence of revision numbers in which the object
;; changed.

(defn get-revlist [opts show object]
  (let [x (java.io.ByteArrayInputStream. (.getBytes (log opts show object)))]
    (for [e (try
              (xml-seq (parse x))
              (catch Exception e (errmsg)))
          :when (seq (:attrs e))]
    (:revision (:attrs e)))))

;; Return a sequence of revision pairs for potential comparison -- e.g. for the
;; revlist (9 8 6 4 1), return (8 9 6 8 4 6 1 4).

(defn get-revpairs [revlist object]
  (interleave (rest revlist) (butlast revlist)))

;; Return a string containing the xml-formatted svn log for the requested
;; object.

(defn log [opts show object]
  (let [cmd-components (if opts ["svn" "log" shhh "--xml" opts object]
                                ["svn" "log" shhh "--xml"      object])]
    (if show (show-cmd cmd-components))
    (print "Fetching log... ")
    (flush)
    (let [result (:out (apply sh cmd-components))]
      (println)
      result)))

;; Obtain a diff from svn for the given object between the specified revisions.
;; Break each diff into blocks (delineated by the text "Index: "), where each
;; block describes one file. The block's first line is the filename, and lines
;; beginning with a single '+' or '-' are the changes. Construct and return a
;; vector of the names of files where the filter term appears on a changed line.
;; If no filter term is given, a changed file automatically matches.

(defn matching-files [r1 r2 object filt show insens]
  (let [raw (:out (diff r1 r2 object show))
        blocks (rest (split raw #"^Index: |\nIndex: "))
        filt (if insens (str "(?i)" filt) filt)]
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

(defn svndiff [r1 r2 filename object show]
  (let [object (baseobject object)
        r (re-pattern (str "^.*/" filename "$"))
        p (if (uri? object)
            (if (re-matches r object) object (str object "/" filename))
            filename)
        v (vpaths r1 r2 p object)
        cmd-components ["svn" "diff" (first v) (second v)]]
    (if show (show-cmd cmd-components))
    (apply sh cmd-components)))

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
        insens (:case-insensitive options)
        optsd (:diff-opts options)
        optsl (:log-opts options)
        show (:show-cmds options)]
    (if (:help options) (usage summary 0))
    (if (not object)
      (usage summary 1)
      (let [revlist (get-revlist optsl show object)
            revpairs (get-revpairs revlist object)]
        (diff-revpairs object filt revpairs show insens)
        (shutdown-agents)))))
