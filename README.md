revdiff
=======
[![Build Status](https://travis-ci.org/maddenp/revdiff.svg)](https://travis-ci.org/maddenp/revdiff)

Performs an _svn diff_ on each pair of revisions between which the object supplied as the first argument changed. The most recent changes are shown first. The object may be an svn URI or the name of a versioned filesystem object (i.e. a file or directory in a checked-out working copy).

###Build
Install [Leiningen](http://leiningen.org/) if you don't have it, then: `lein uberjar`

###Run
````
Usage: revdiff [options] object [regexp]

  Options:

  -h, --help               Show usage information
  -i, --case-insensitive   Treat regexp as case-insensitive
  -d, --diff-opts options  Quoted list of options to pass to svn diff
  -l, --log-opts options   Quoted list of options to pass to svn log
  -s, --show-cmds          Show svn commands as they are issued

  object: svn URI or name of versioned object in working-copy
  regexp: only show diffs where a changed line matches regexp
````
The _revdiff_ wrapper script looks for the Leiningen-generated _revdiff.jar_ in the same directory as the script itself. It may be convenient to edit this script for your own use.

###Notes on options
If the optional second argument is present, diffs are only shown only when some changed line matches the given regexp. The regexp must match a **complete** line, so e.g. to filter by _term_, supply the regexp _".\*term.\*"_.

Use the _-d_ and _-l_ options with caution. Some svn options (like _--stop-on-copy_ for the _svn log_ command) are quite useful; others may interfere with options automatically passed to svn (like _--xml_). The _-s_ command can be used to show the actual svn commands being executed, and may be useful for debugging problems encountered when using _-d_ or _-l_.

### Examples
* Show all diffs on _src/init.F90_, over the full history of branch _b_ of _project_, via URL:
````
  revdiff -l '--stop-on-copy' https://example.com/svn/project/branches/b/src/init.F90
````
* Show diffs on working-copy source files where a C _#ifdef_ appeared or disappeared:
````
  revdiff src/ "^\s*#ifdef.*"
````
* Show diffs on _dir/file_ on the trunk of _project_, starting at revision 101 and working backwards, where the word _calgary_ (case insensitive) appears on one or more changed lines:
````
  revdiff -i https://example.com/svn/project/trunk/dir/file@101 ".*calgary.*"
````
### Prerequisites
- The _svn_ binary must be on your path.
- Set up your svn client to display diffs as you like them. A graphical diff tool like [xxdiff](http://furius.ca/xxdiff) is recommended (you may need a [glue script](http://svnbook.red-bean.com/en/1.6/svn.advanced.externaldifftools.html#svn.advanced.externaldifftools.diff)). One benefit of this configuration is that the diff of the next revision pair is not displayed until you finish with the current one and exit the diff tool.
- You must have previously authenticated with the svn repository in question and allowed your credentials to be cached (interactive authentication is not supported). If you are having trouble, try issuing a command like _svn log object_, where _object_ is a URI or a filesystem object. This should give you an opportunity to authenticate and cache your credentials.
