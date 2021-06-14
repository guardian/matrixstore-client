package commands
import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.util.{Success, Try}

class Disconnect extends BaseCommand {
  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = {
    session.activeConnection match {
      case None=>
        terminal.writer().println("There is no active connection to disconnect")
        terminal.writer().flush()
        Success(session)
      case Some(mxs)=>
        terminal.writer().println("Shutting down active connection...")
        terminal.writer().flush()
        mxs.dispose()
        terminal.writer().println("Done")
        terminal.writer().flush()
        Success(session.copy(activeConnection = None, activeVaultId = None))
    }
  }
}
