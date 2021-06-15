# matrixstore-client

A simple commandline client for accessing files on a MatrixStore device

## How to build

Once you have a v1.8 JDK installed and SBT (**NOTE** - the ObjectMatrix
libraries _require_ JDK 1.8 and nothing newer) then you can simply build by:

```
sbt docker:publishLocal
```

Or you can run "from source" by:

```
sbt run
```

## How to use it

In order to run, either do it straight through `sbt` as above or run in Docker:

```
docker run --rm -it guardianmultimedia/matrixstore-client:DEV
```

No ports are necessary, but you'll need to make sure an interactive terminal
is provisoßned (the `-it` argument above)

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
	disconnect | dis       - close the current connection opened by `connect`
	exit                   - leaves the program
	stacktrace             - show detailed information for the last error that happened
	search {query-string}  - perform a search on the MatrixStore
	lookup {filepath}      - perform a basic search on MXFS_FILEPATH and return full metadata. Indended to be used for single files.
	md5 {oid}              - calculate appliance-side checksum for the given object. Get the OID from `search` or `lookup`.
	delete {oid}           - delete the object from the appliance. Note that if there is no Trash period configured, the file will be gone baby gone.
	set timeout {value}    - changes the async timeout parameter. {value} is a string that must parse to FiniteDuration, e.g. '1 minute' or '2 hours'. Default is one minute.
	set pagesize {value}   - changes the number of rows to be printed before pausing for QUIT/CONTINUE when performing a search
	show headers {on|off}  - set whether to show the header line when searching

Write any command followed by the word 'help' to see a brief summary of any options that are required
{vault-id}> 
```

