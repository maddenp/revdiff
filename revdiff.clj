; Copyright 2012 Paul Madden (maddenp@colorado.edu)
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
; http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

; DESCRIPTION
;
; Performs an "svn diff" on each pair of revisions between which the item
; supplied as the first argument changed. The most recent change is shown
; first. The item may name an svn uri or, if you are in an svn working copy,
; the name of a versioned filesystem object. If a second agument is supplied,
; diffs are restricted to those in which the argument was present on a changed
; line. You may supply substrings, or use regular expressions in double-quotes,
; i.e.: word or "\bword\b". Assumptions: (1) The svn binary is available on your
; path; (2) You have previously authenticated with the svn server and have
; allowed your credentials to be cached (there's no support here for interactive
; authentication); (3) You have configured the behavior of "svn diff" to your
; liking.

; TODO add a debug toggle and some related verbose prints?
; TODO is --stop-on-copy on log really a good idea?
; TODO maybe print log message / committer info (could use awt/swing popup)?
; TOTO pass all '--' options (e.g. --username) to svn?

(ns revdiff
  (:import [java.io StringBufferInputStream])
  (:use [clojure.java.shell :only (sh)])
  (:use [clojure.string :only (split split-lines trim)])
  (:use [clojure.xml :only (parse)]))

(def args *command-line-args*)  ; for brevity's sake
(def item (second args))        ; the query subject
(def shhh "--non-interactive")  ; suppress svn user prompts
(def prog (first args))         ; name of the wrapper script
(def term (second (rest args))) ; the (optional) filter term

; A string containing the xml-formatted svn log for the requested item. Stop on
; copy to avoid following the history of a differently-named object.

(defn log [] (sh "svn" "log" shhh "--stop-on-copy" "--xml" item))

; Try to explain why this has failed.

(defn errmsg []
  (println "\nError retrieving repository information. Perhaps:\n")
  (println "- The supplied filename or URI is invalid, or")
  (println "- Your valid svn authentication credentials are not cached.")
  (println))

; A newest-first sequence of revision numbers in which the item changed

(defn revlist []
  (for [e
        (try
          (xml-seq (parse (java.io.StringBufferInputStream. (:out (log)))))
          (catch Exception x (errmsg)))
        :when (seq (:attrs e))
        ]
    (:revision (:attrs e))))

; A sequence of revision pairs for potential comparison -- e.g. for the revlist
; (9 8 6 4 1), return (8 9 6 8 4 6 1 4).

(defn revpairs [revlist]
  (when (seq (rest revlist))
    (concat (list (first (rest revlist)) (first revlist))
            (revpairs (rest revlist)))))

; Is path a uri? If it contains :// assume it is.

(defn uri? [path] (re-matches #".*://.*" path))

; If item is a uri, the "-rn:m" revision-range format will not work if item is
; no longer present in the head revision, in which case we have to use the
; "item@n item@m" revision-specification format. Return the correctly formatted
; versioned-path(s) string for use by a diff command.

(defn vpaths [r1 r2 path]
  (if (uri? item)
    (list (str path "@" r1) (str path "@" r2))
    (list (str "-r" r1 ":" r2) path)))
  
; A string containing the output of "diff" on the two svn revision of item.

(defn txtdiff [r1 r2]
  (let [v (vpaths r1 r2 item)]
    (sh "svn" "diff" "--diff-cmd" "diff" shhh (first v) (second v))))

; A sequence (possibly nil) of the changed lines (denoted by a leading + or -)
; from the given text diff, filtered by the filter term, if provided.

(defn matches? [txtdiff]
  (let [restr (if term (str "[+-][^+-].*" term ".*") ".*")]
    (seq (filter #(re-matches (re-pattern restr) %) txtdiff))))

; A sequence of files from the revision-pair diff with changed lines matching the
; filter term. The first line of each block contains the file/path name from
; the text diff header, with "Index:" stripped off. The rest of the lines contain
; the +/- changed lines. Process all blocks recursively to produce the list of
; matching files.

(defn matchfiles [blocks]
  (when (seq blocks)
    (let [lines (split-lines (first blocks))
          fname (trim (first lines))
          match (matches? (rest lines))]
      (concat (when match (list fname)) (matchfiles (rest blocks))))))

; A wrapper for matchfiles: provides a sequence of blocks from the text diff,
; each block delineated by an "Index:" tag (either in the first position of
; the string, or at the start of a line). Returns the sequence of filenames
; produced recursively by matchfiles.

(defn filediffs [r1 r2]
  (matchfiles (rest (split (:out (txtdiff r1 r2)) #"^Index: |\nIndex: "))))

; Run "svn diff" on the file/pathname in index. The format of the file/path name
; following "Index:" in diff's output depends on the argument: If the argument is
; a filesystem pathname, the index name is absolute, and we can simply use that.
; If the argument is a uri, the index name is relative to the base item name. If
; the base item name was the uri to a file (and therefore a complete pathname) we
; can use item; if it was the uri to a directory, we have to concatenate the item
; and index to form a complete pathname.

(defn diff [r1 r2 index]
  (let [r (re-pattern (str "^.*/" index "$"))
        p (if (uri? item)
            (if (re-matches r item) item (str item "/" index))
            index)
        v (vpaths r1 r2 p)]
    (sh "svn" "diff" (first v) (second v))))

; Given a list of revision pairs, diff those for which the given filter term (if
; any) is present on a changed line in that pair. If no filter term is given,
; diff all changed files from all revision pairs.

(defn diffrevpairs [revpairs]
  (when (seq revpairs)
    (let [r1 (first revpairs) r2 (first (rest revpairs))
          fd (filediffs r1 r2)]
      (println (str "Checking: r" r1 " vs r" r2))
      (doseq [index fd] (diff r1 r2 index))
      (diffrevpairs (rest (rest revpairs))))))

(defn usage []
  (println (str "\nUsage: " prog " item [term]\n"))
  (println "  item : svn uri or name of versioned working-copy object")
  (println "  term : only show diffs with term present on a changed line\n"))

(if (not item) (usage) (diffrevpairs (revpairs (revlist))))
