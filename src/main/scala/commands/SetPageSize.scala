package commands
import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.util.Try

class SetPageSize extends BaseCommand {
  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = Try {
    val newValue = params(2).toInt
    terminal.writer().println(s"Pagesize is now $newValue")
    terminal.writer().flush()
    session.copy(itemsPerPage = newValue)
  }
}
