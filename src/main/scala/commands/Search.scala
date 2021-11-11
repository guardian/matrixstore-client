package commands
import akka.actor.ActorSystem
import akka.stream.{KillSwitches, Materializer}
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Success, Try}

class Search extends BaseCommand {
  import SearchFunctions._

  val baseIncludeFields = Array(
    "MXFS_PATH",
    "MXFS_FILENAME",
    "DPSP_SIZE",
    "__mxs__length",
    "MXFS_MODIFICATION_TIME",
    "MXFS_ACCESS_TIME",
    "MXFS_CREATION_TIME",
    "MXFS_ARCHIVE_TIME",
    "MXFS_INTRASH"
  )

  override def run(params: Seq[String], session: Session)
                  (implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = {
    (session.activeConnection, session.activeVaultId) match {
      case (Some(mxs), Some(vaultId))=>
        Try {
          val ks = KillSwitches.shared("user-quit")
          val sinkFactory = new PrintResultSink(baseIncludeFields ++ session.fields, session.fields.toArray, session.itemsPerPage, session.showHeaders, ks)
          Await.result(
            doSearch(params, mxs, vaultId, baseIncludeFields ++ session.fields, sinkFactory, ks),
            session.asyncTimeout
          )
        }.map(_=>session)
      case _=>
        terminal.writer().println("You must connect to a vault before you can search")
        terminal.writer().flush()
        Success(session)
    }
  }
}
