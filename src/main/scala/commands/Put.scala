package commands

import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.concurrent.Await
import scala.util.{Success, Try}

class Put extends BaseCommand {
  def showHelp(implicit terminal: Terminal) = {
    terminal.writer().println("`put` uploads the given file from the appliance")
    terminal.writer().println("Usage: put {path-to-localfile} [{description}]")
    terminal.writer().println("\t{path-to-localfile} is the path to upload")
    terminal.writer().println("\t{description} is an optional string to put into the MXFS_DESCRIPTION field")
    terminal.flush()
  }

  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = {
    if(params.length<2 || params.length>3) {
      showHelp
      return Success(session)
    }
    val
    (session.activeConnection, session.activeVaultId) match {
      case (Some(mxs), Some(vaultId))=>
        Try {
          Await.result(
            withVaultAsync(mxs, vaultId) { vault =>
              performUpload(vault, params)
  }
}
