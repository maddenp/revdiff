revdiff
=======

View (reverse) evolution of a Subversion object, with optional filtering.

Performs an "svn diff" on each pair of revisions between which the item supplied as the first argument changed. The most recent change is shown first. The item may name an svn URI or the name of a versioned filesystem object (an item in a working copy). The svn binary is invoked with the --stop-on-copy flag.

If the optional second argument is supplied, only diffs in which the argument was present on a changed line are shown. You may supply substrings, or use regular expressions in double-quotes (e.g. word or "\bword\b"). For a case-insensitive regex, use "(?i)regex".

### Prerequisites

- The svn binary must be available on your path.
- Set up your svn client to display diffs to your liking. A graphical diff tool like [xxdiff](http://furius.ca/xxdiff) is recommended (you may need a [glue script](http://svnbook.red-bean.com/en/1.6/svn.advanced.externaldifftools.html#svn.advanced.externaldifftools.diff)). One benefit of this configuration is that the diff of the next revision pair is not displayed until you finish with the current one and exit the diff tool.
- You have previously authenticated with the svn repository in question and have allowed your credentials to be cached (there's no support here for interactive authentication).

### Usage

`revdiff item [term]`
