package commands
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.om.mxs.client.japi.{MatrixStore, Vault}
import helpers.MatrixStoreHelper
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

class LookupFilename extends BaseCommand {
  def performLookup(filename:String)(implicit vault:Vault, ec:ExecutionContext, mat:Materializer) = {
    for {
      results <- Future.fromTry(MatrixStoreHelper.findByFilename(vault, filename, Seq("oid", "MXFS_FILENAME", "__mxs__length","__mxs__location")))
      fullMeta <- Future.sequence(results.map(_.getMetadata))
    } yield fullMeta
  }

  def showHelp(implicit terminal: Terminal) = {
    terminal.writer().println("`lookup` performs a basic filename lookup. The `search` command is generally preferred to this.")
    terminal.writer().println("Usage: lookup \"filename\".  Don't forget to surround the filename in quotes if it includes spaces.")
    terminal.writer().flush()
  }

  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] =  {
    implicit val ec:ExecutionContext = actorSystem.dispatcher
    if(params.length!=2) {
      showHelp
      return Success(session)
    }

    (session.activeConnection, session.activeVaultId) match {
      case (Some(mxs), Some(vaultId))=>
        val result = withVaultAsync(mxs, vaultId) { vault=>
          for {
            filename <- Future {params(1)}
            result <- performLookup(filename)(vault, actorSystem.dispatcher, mat)
          } yield result
        }

        Try {
          Await.result(
            result.map(results => {
              terminal.writer().println(s"Got ${results.length} results:")
              results.foreach(entry => {
                terminal.writer().println(s"${entry.oid}\t${entry.getFileSize.map(_.toString).getOrElse("(no size)")}\t\t${entry.pathOrFilename.getOrElse("(no name)")}")
                entry.attributes.map(meta=>{
                  meta.stringValues.foreach(kv=>terminal.writer().println(s"\t${kv._1}: ${kv._2}"))
                  meta.intValues.foreach(kv=>terminal.writer().println(s"\t${kv._1}: ${kv._2}"))
                  meta.longValues.foreach(kv=>terminal.writer().println(s"\t${kv._1}: ${kv._2}"))
                  meta.boolValues.foreach(kv=>terminal.writer().println(s"\t${kv._1}: ${kv._2}"))
                  meta.floatValues.foreach(kv=>terminal.writer().println(s"\t${kv._1}: ${kv._2}"))
                })
              })
              terminal.writer().flush()
              session
            }),
            session.asyncTimeout)
        }
      case _=>
        terminal.writer().println("You must have an active connection before you can lookup.  Use the `connect` command to establish connection")
        Success(session)
    }

  }
}
