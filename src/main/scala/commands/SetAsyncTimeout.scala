package commands
import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

/**
 * set the async timeout parameter in the given session
 */
class SetAsyncTimeout extends BaseCommand {
  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = {
    val stringValue = params(2)

    Try {
      val dur = Duration.create(stringValue)
      if(dur.isFinite) {
        terminal.writer().println(s"Timeout is now ${dur.toString}")
        session.copy(asyncTimeout = dur.asInstanceOf[FiniteDuration]) //see https://stackoverflow.com/questions/33678853/scala-finiteduration-from-string
      } else {
        terminal.writer().println("You must set a finite duration for the timeout")
        terminal.writer().flush()
        session
      }
    }
  }
}
