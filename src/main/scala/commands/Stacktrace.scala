package commands
import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.util.{Success, Try}

class Stacktrace extends BaseCommand {
  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat:Materializer): Try[Session] = {
    session.lastException match {
      case Some(err)=>
        err.printStackTrace(terminal.writer())
        Success(session)
      case None=>
        terminal.writer().println("There has not been an exception yet")
        Success(session)
    }
  }
}
