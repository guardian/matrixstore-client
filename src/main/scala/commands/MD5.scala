package commands
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.om.mxs.client.japi.MatrixStore
import helpers.MatrixStoreHelper
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

/**
 * requests an appliance-side MD5 checksum
 */
class MD5 extends BaseCommand {
  def showHelp(implicit terminal:Terminal) = {
    terminal.writer().println("`md5` requests an appliance-side MD5 checksum, i.e. we don't stream data to the app and calculate it here but ask the applicance to do it for us.")
    terminal.writer().println("Usage: md5 {object-id}")
    terminal.writer().println("\t{object-id} is the UUID-like ID of the file to be deleted. You can obtain this from the first column of `search` or `lookup`")
    terminal.flush()
  }

  def asyncLookup(mxs:MatrixStore, vaultId:String, params:Seq[String])(implicit ec:ExecutionContext) = {
    withVaultAsync(mxs, vaultId) { vault=>
      for {
        oid <- Future.fromTry(Try { params(1) })
        obj <- Future.fromTry(Try { vault.getObject(oid) })
        result <- MatrixStoreHelper.getOMFileMd5(obj)
      } yield result
    }
  }

  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = {
    implicit val ec:ExecutionContext = actorSystem.dispatcher

    if(params.length!=2) {
      showHelp
      return Success(session)
    }

    (session.activeConnection, session.activeVaultId) match {
      case (Some(mxs), Some(vaultId))=>
        Try {
          Await.result(asyncLookup(mxs, vaultId, params), session.asyncTimeout)
        }.flatten.map(md5=>{
          terminal.writer().println(s"$md5 ${params(1)}")
          terminal.writer().flush()
          session
        })
    }
  }
}
