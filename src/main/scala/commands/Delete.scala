package commands
import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.util.{Success, Try}

class Delete extends BaseCommand {
  def showHelp(implicit terminal: Terminal) = {
    terminal.writer().println("`delete` deletes the given file from the appliance")
    terminal.writer().println("Usage: delete {object-id}")
    terminal.writer().println("\t{object-id} is the UUID-like ID of the file to be deleted. You can obtain this from the first column of `search` or `lookup`")
    terminal.flush()
  }

  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = {
    if(params.length!=2) {
      showHelp
      return Success(session)
    }

    (session.activeConnection, session.activeVaultId) match {
      case (Some(mxs), Some(vaultId))=>
        withVault(mxs, vaultId) { vault=>
          for {
            oid <- Try { params(1) }
            obj <- Try { vault.getObject(oid) }
            result <- Try { obj.delete()}
            _ <- Try { terminal.writer().println(s"Deleted object $oid") }
            _ <- Try { terminal.writer().flush() }
          } yield result
        }.map(_=>session)
      case _=>
        terminal.writer().println("You must connect to a vault before you can delete anything. Try `connect` or `help`.")
        terminal.writer().flush()
        Success(session)
    }
  }
}
