package commands

import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.util.Try

trait BaseCommand {
  def run(params:Seq[String], session:Session)(implicit terminal:Terminal, lineReader:LineReader, actorSystem: ActorSystem, mat:Materializer):Try[Session]
}
