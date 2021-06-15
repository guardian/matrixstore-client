package commands
import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.util.{Success, Try}

class ShowHeaders extends BaseCommand {
  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = Try {
    params(2).toLowerCase() match {
      case "off"|"false"|"no"=>
        session.copy(showHeaders = false)
      case "on"|"true"|"tes"=>
        session.copy(showHeaders = true)
      case _=>
        terminal.writer().println("`show headers` sets whether to print a header line when searching.")
        terminal.writer().println("Usage: show headers {on|off}")
        terminal.writer().println("This should be fairly self-explanatory.....")
        terminal.writer().flush()
        session
    }
  }
}
