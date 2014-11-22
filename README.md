revdiff
=======

View (reverse) evolution of a Subversion object, with optional filtering. Performs an _svn diff_ on each pair of revisions between which the object supplied as the first argument changed. The most recent changes are shown first. The object may be an svn URI or the name of a versioned filesystem object (i.e. a file or directory in a checked-out working copy). The svn binary is invoked with the _--stop-on-copy_ flag.

###Build

Install [Leiningen](http://leiningen.org/) if you don't have it, then:

`lein uberjar`

###Run

````
Usage: revdiff object [regexp]

  object : svn URI or name of versioned object in working-copy
  regexp : only show diffs where a changed line matches regexp
````

The _revdiff_ wrapper script expects to find the Leiningen-generated _revdiff.jar_ in the same directory as the script itself. (A symlink is provided in this repository.) It may be convenient to edit this script for your own use.

If the optional second argument is present, diffs are only shown only when some changed line matches the given regexp. The regexp must match a *complete* line, so to filter by _term_ you would supply the regexp _".\*term.\*"_. For case-insensitivity, prefix _regexp_ with _(?i)_, e.g. _"(?i).\*term.\*"_.

### Prerequisites

- The _svn_ binary must be available on your path.
- Set up your svn client to display diffs to your liking. A graphical diff tool like [xxdiff](http://furius.ca/xxdiff) is recommended (you may need a [glue script](http://svnbook.red-bean.com/en/1.6/svn.advanced.externaldifftools.html#svn.advanced.externaldifftools.diff)). One benefit of this configuration is that the diff of the next revision pair is not displayed until you finish with the current one and exit the diff tool.
- You have previously authenticated with the svn repository in question and have allowed your credentials to be cached (_revdiff_ does not support interactive authentication). If you are having trouble, try issuing a command like _svn log object_, where _object_ is a URI or a filesystem object.
