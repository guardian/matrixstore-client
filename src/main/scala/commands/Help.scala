package commands
import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.util.{Success, Try}

class Help extends BaseCommand {
  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat:Materializer): Try[Session] = {
    Seq(
      "Available commands:",
      "\tconnect                - connects to an appliance",
      "\tdisconnect | dis       - close the current connection opened by `connect`, does not show help",
      "\texit                   - leaves the program, does not show help",
      "\tstacktrace             - show detailed information for the last error that happened",
      "\tsearch {query-string}  - perform a search on the MatrixStore",
      "\tlookup {filepath}      - perform a basic search on MXFS_FILEPATH and return full metadata. Intended to be used for single files.",
      "\tmd5 {oid}              - calculate appliance-side checksum for the given object. Get the OID from `search` or `lookup`.",
      "\tmeta {oid}             - show all metadata fields associated with the given object. Get the OID from `search` or `lookup`.",
      "\tget {oid}              - download the file content of {oid} to the current directory.",
      "\tdelete {oid}           - delete the object from the appliance. Note that if there is no Trash period configured, the file will be gone baby gone.",
      "\tsearchdel {query-string} - delete every object that matches the given query string. The list of objects to delete is shown first and you are prompted whether to continue or not",
      "\tset timeout {value}    - changes the async timeout parameter. {value} is a string that must parse to FiniteDuration, e.g. '1 minute' or '2 hours'. Default is one minute.",
      "\tset pagesize {value}   - changes the number of rows to be printed before pausing for QUIT/CONTINUE when performing a search",
      "\tshow headers {on|off}  - set whether to show the header line when searching",
      "",
      "Write any command without arguments to see a brief summary of any options that are required"
    ).foreach(line=>terminal.writer().println(line))
    terminal.writer().flush()
    Success(session)
  }
}
