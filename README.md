# matrixstore-client

A simple commandline client for accessing files on a MatrixStore device

# Prerequisites

You'll need Java v1.8 installed. Note that _later_ Java is NOT supported, owing to trouble with the third-party libraries.
On a Mac, you can check your Java version like so:
```
/usr/libexec/java_home -V
Matching Java Virtual Machines (5):
    15.0.2, x86_64:	"OpenJDK 15.0.2"	/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home
    11.0.11, x86_64:	"OpenJDK 11.0.11"	/Users/myself/Library/Java/JavaVirtualMachines/adopt-openjdk-11.0.11/Contents/Home
    1.8.0_282, x86_64:	"OpenJDK 8"	/Library/Java/JavaVirtualMachines/openjdk-8.jdk/Contents/Home
    1.6.0_65-b14-468, x86_64:	"Java SE 6"	/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
    1.6.0_65-b14-468, i386:	"Java SE 6"	/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home

/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home
```

You will see that my default is currently 15. To change this, run
```bash
declare -x JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-8.jdk/Contents/Home
```
(get the right path from the output of java_home)


## How to get it

Go to the Release page on github, find the release you want and download the JAR file.
Save it onto your local machine then open a Terminal (or your Windows equivalent) and go to that location

In order to run from a built JAR, either build a JAR as above or download from Github releases then:
```
java -jar {path-to-downloaded-jar}
```

## How to use it

**How do I download a file from Matrixstore?** - read through to the bottom of this document and don't skip :-p

TL;DR: 
1. Log into the vault
2. Use the `search` or `lookup` commands to find what you are interested in
3. Find the OID of the file. This is the long thing that looks like a UUID with another part at the end
4. Run `get` then the OID. The file will be downloaded to the current working directory of your machine, with a filename taken from the MXFS_FILE field.


The first thing you'll need to do is to make a connection to a cluster.  You
do this with the `connect` command:

```
> connect {ip-address-list} {vault-id} {user-name}
Connecting to ... on ... as ... 
Password > 
{vault-id}> 
```

- `{ip-address-list}` can be either the IP address of a single cluster node or a comma-
separated list of multiple nodes.
- `{vault-id}` is the UUID of the vault you want to connect to
- `{user-name}` is the user name to use when logging in. You are then prompted
for a password, which you type at the prompt and is hidden behind * characters.
  
Once you have a connection, the prompt changes to show the vault id behind the '>' 
character.

### Credentials

matrixstore-client expects MatrixStore 4 credentials, specifically a username/password
credential as opposed to an access key credential.

Earlier version credentials (.vault files) are **not** supported.

Consult the MatrixStore documentation for how to create and provision users.

## What now?

Once you're logged in, use the `help` command to list out the actions that can be done:

```
{vault-id}> help
Available commands:
	connect                - connects to an appliance
	disconnect | dis       - close the current connection opened by `connect`, does not show help
	exit                   - leaves the program, does not show help
	stacktrace             - show detailed information for the last error that happened
	search {query-string}  - perform a search on the MatrixStore
	lookup {filepath}      - perform a basic search on MXFS_FILEPATH and return full metadata. Intended to be used for single files.
	md5 {oid}              - calculate appliance-side checksum for the given object. Get the OID from `search` or `lookup`.
	meta {oid}             - show all metadata fields associated with the given object. Get the OID from `search` or `lookup`.
	get {oid}              - download the file content of {oid} to the current directory.
	delete {oid}           - delete the object from the appliance. Note that if there is no Trash period configured, the file will be gone baby gone.
	searchdel {query-string} - delete every object that matches the given query string. The list of objects to delete is shown first and you are prompted whether to continue or not
	set timeout {value}    - changes the async timeout parameter. {value} is a string that must parse to FiniteDuration, e.g. '1 minute' or '2 hours'. Default is one minute.
	set pagesize {value}   - changes the number of rows to be printed before pausing for QUIT/CONTINUE when performing a search
	show headers {on|off}  - set whether to show the header line when searching

Write any command without arguments to see a brief summary of any options that are required
{vault-id}> 
```

## Some notes on quoting and escaping

This program uses the jline3 library to provide a commandline terminal.

Just like a regular unix shell, arguments are separated by space characters. Multiple spaces are compressed to one.
If you want to have spaces in an argument, you must enclose it within double-quotes, like this:

```
lookup "path/to/my/file with space.ext"
```

If there are no spaces, then the following two are equivalent:

```
lookup path/to/my/file.ext
lookup "path/to/my/file.ext"
```

If you need to send quotes as _part_ of your argument, for example in a query string, then you must escape them with the 
`\` character:

```
search "MXFS_FILENAME:\"my file with spaces.ext\""
```

This will send the query string `MXFS_FILENAME:"my file with spaces.ext"`.  If you were to leave out the inner quotes,
then the command would still be sent but the MatrixStore appliance would probably send back either an error or
unexpected results as it has not interpreted the query how you want.

If you were to forget to escape the inner quotes, then you'd probably get a syntax error from the shell; it would consider
there to be four parameters on the search command being `MXFS_FILENAME:my`, `file`, `with`, `spaces.ext`.

## Some notes on searching

The `search` command paginates by default, i.e. it will show you one "page" of results
and then pause, asking you whether to continue or quit.

Don't be afraid to run `search *` to find everything, as you won't lock your terminal up
or be stuck waiting for it to spool through millions of results.

The default page size is 10, but you can change this with the `set pagesize` command.

You'll notice that the `search` command in the list above takes a single parameter called `query-string`.  So you're probably
wondering what the hell this is.

Queries are based upon Lucene 5.3.1 syntax - which means that they are basically the same as what you would put into Kibana.

In general, you specfy a number of terms which are then combined together.  Terms are separated by spaces, so if you want
a space in your term you must quote the term with the `"` character.

You can optionally specify a field to search for the term on, e.g. `search "MXFS_FILENAME:\"my file with spaces.ext\""`

If you were to type `search "MXFS_FILENAME:my file with spaces.ext"`, then it would search for `my` in the field MXFS_FILENAME
and then for `file` in any field, `with` in any field and `spaces.ext` in any field and OR the results together.
Net result - a ton of unexpected results.

 Wildcards are supported, as per section 4.1.3 of "Basic Searching":
> The single character wildcard search looks for terms that match that with the single character replaced. For example, to search for "text" or "test" you can use the search:
> ```
> te?t
> ```
> 
> Multiple character wildcard searches looks for 0 or more characters. For example, to search for test, tests or tester, you can use the search:
> ```
> test*
> ```
> You can also use the wildcard searches in the middle of a term.
> ```
> te*t
> ```

If you want to discover a bit more about the metadata, then you can run
```
{vault-id}> search *
2021-06-17 10:42:47,181 [INFO] from streamcomponents.OMFastSearchSource$$anon$1 in matrixstore-client-akka.actor.default-dispatcher-11 - Connection established
1215748_KP-31725704_poster
0cb01940-3efc-11eb-887a-cb5c82a97316-11
14423
------------
.
.
.
Press Q [ENTER] to quit or [ENTER] for the next page

```
which will show you the first page of results as above.
You can see that the first result is a file with MXFS_FILENAME of `1215748_KP-31725704_poster`
and an ObjectMatrix ID (oid) of `0cb01940-3efc-11eb-887a-cb5c82a97316-11`.

Copy-pasting the OID value onto the `meta` command like so will show you all of the metadata fields for the given file:
``` 
{vauld-id}> meta 0cb01940-3efc-11eb-887a-cb5c82a97316-11
0cb01940-3efc-11eb-887a-cb5c82a97316-11	14423		1215748_KP-31725704_poster
	MXFS_FILENAME_UPPER: 1215748_KP-31725704_POSTER
	GNM_PROJECT_ID: 12806
	GNM_TYPE: Metadata
	MXFS_PATH: 1215748_KP-31725704_poster
	GNM_COMMISSION_ID: 2579
	MXFS_FILENAME: 1215748_KP-31725704_poster
	GNM_WORKING_GROUP_NAME: Multimedia News
	GNM_PROJECT_NAME: 221216 James B AM
	MXFS_MIMETYPE: application/xml
	MXFS_DESCRIPTION: Metadata for 1215748_KP-31725704.null
	GNM_COMMISSION_NAME: 19 December 2016 weekly agency news
	MXFS_CREATIONDAY: 16
	MXFS_COMPATIBLE: 1
	MXFS_ARCHYEAR: 2020
	MXFS_ARCHDAY: 16
	MXFS_CREATIONMONTH: 12
	MXFS_CREATIONYEAR: 2020
	MXFS_CATEGORY: 4
	MXFS_ARCHMONTH: 12
	MXFS_MODIFICATION_TIME: 1608139573806
	__mxs__length: 14423
	MXFS_CREATION_TIME: 1608139573561
	MXFS_ACCESS_TIME: 1608139573806
	DPSP_SIZE: -1
	MXFS_ARCHIVE_TIME: 1608139573711
	GNM_MISSING_PROJECTINFO: false
	MXFS_INTRASH: false
	GNM_HIDDEN_FILE: false
{vauld-id}>
```
You'll notice that there are MXFS_PATH and MXFS_FILENAME fields present.
In general, MXFS_PATH is the full path _relative_ to the Vidispine storage that 
the item originated on (so, if something is in the Assets folder you would expect the
first portion to be the working group; if something is in the Masters folder you would
expect it to not have a path).
You'll also notice a bunch of GNM fields to help you isolate where the file came
from.  All of these are searchable just the same as the path/filename, e.g.

```
{vault-id}> search "GNM_PROJECT_ID:12806 AND GNM_TYPE:Metadata"
```

See `Basic Searching.md` for more information about boolean query terms.


`MXFS_FILENAME` is generally the bare filename without the path.
**NOTE that the `lookup` command is an EXACT MATCH on the `MXFS_FILENAME` field only**
If you don't get the results you expect with `lookup`, then use `search` to try to pin
down the file and `meta` to examine the metadata to see what is wrong.
Sometimes, path segments exist in `MXFS_FILENAME` which can be confusing.


For full details, you should read chapter four of the "Content Search" PDF guide that comes with the ObjectMatrix SDK.

I have extracted the relevant parts from version 3.2 into the "Basic Searching.md" file for
convenience but it's always worth double-checking the appliance version and getting hold
of the right guide directly from ObjectMatrix.

The search queries are not validated or interpreted by this software, they are just passed over blindly.

## How to download something?

1. Log into the vault
2. Use the `search` or `lookup` commands to find what you are interested in
3. Find the OID of the file. This is the long thing that looks like a UUID with another part at the end
4. Run `get` then the OID. The file will be downloaded to the current working directory of your machine, with a filename taken from the MXFS_FILE field.

# How to build

Once you have a v1.8 JDK installed and SBT (**NOTE** - the ObjectMatrix
libraries _require_ JDK 1.8 and nothing newer) then you can simply build by either:

```
sbt assembly
```
will produce a single, runnable JAR file in the `target/scala-2.13` directory or:
```
sbt docker:publishLocal
```
will produce a Docker image on your local Docker daemon.

Or you can run "from source" by:

```
sbt run
```