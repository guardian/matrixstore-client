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
      "\tconnect - connects to an appliance",
      "\texit    - leaves the program",
      "\tstacktrace - show detailed information for the last error that happened",
      "\tsearch  - perform a search on the MatrixStore",
      "\tset timeout {value} - changes the async timeout parameter. {value} is a string that must parse to FiniteDuration, e.g. '1 minute' or '2 hours'. Default is one minute.",
      "\tshow headers {on|off} - set whether to show the header line when searching",
      "",
      "Write any command followed by the word 'help' to see a brief summary of any options that are required"
    ).foreach(line=>terminal.writer().println(line))
    terminal.writer().flush()
    Success(session)
  }
}
