package commands
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{FileIO, GraphDSL, RunnableGraph}
import com.om.mxs.client.japi.{MxsObject, Vault}
import helpers.MetadataHelper
import interpreter.Session
import models.MxsMetadata
import org.jline.reader.LineReader
import org.jline.terminal.Terminal
import streamcomponents.MatrixStoreFileSource

import java.nio.file.Paths
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

class Get extends BaseCommand {
  def showHelp(implicit terminal: Terminal) = {
    terminal.writer().println("`get` downloads the given file from the appliance")
    terminal.writer().println("Usage: get {object-id}")
    terminal.writer().println("\t{object-id} is the UUID-like ID of the file to be deleted. You can obtain this from the first column of `search` or `lookup`")
    terminal.flush()
  }

  def performDownload(vault:Vault, sourceObject:MxsObject, meta:MxsMetadata)(implicit terminal: Terminal, actorSystem: ActorSystem, mat: Materializer) = {
    val filename = meta.stringValues.get("MXFS_FILENAME") match {
      case Some(filenameString)=>Paths.get(filenameString).getFileName
      case None=>
        terminal.writer().println(s"Warning: ${sourceObject.getId} does not have MXFS_FILENAME set on it")
        Paths.get("download.dat")
    }
    terminal.writer().println(s"Downloading to $filename")
    terminal.flush()

    val sinkFactory = FileIO.toPath(filename)
    val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new MatrixStoreFileSource(vault, sourceObject.getId))
      src ~> sink
      ClosedShape
    }
    RunnableGraph.fromGraph(graph).run()
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
          Await.result(
            withVaultAsync(mxs, vaultId) { vault =>
              for {
                obj <- Future.fromTry(Try {
                  vault.getObject(params(1))
                })
                metadata <- MetadataHelper.getAttributeMetadata(obj)
                result <- performDownload(vault, obj, metadata)
              } yield result
            }.flatMap(result => {
              terminal.writer().println(s"Wrote ${result.count} bytes")
              terminal.flush()
              Future.fromTry(result.status)
            }), session.asyncTimeout
          )
        }.map(_=>session)
      case _=>
        terminal.writer().println("You must connect to a vault before you can delete anything. Try `connect` or `help`.")
        terminal.writer().flush()
        Success(session)
    }
  }
}
